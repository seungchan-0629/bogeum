package com.bogeum.domain.aftersales;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.bogeum.common.enums.AsStatus;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.aftersales.model.AfterSalesDto;
import com.bogeum.domain.aftersales.model.AfterSalesRequest;
import com.bogeum.domain.aftersales.model.AfterSalesRequestImage;
import com.bogeum.domain.contract.ContractRepository;
import com.bogeum.domain.contract.model.Contract;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.model.User;

/** 접수 규칙과 임대인 조회의 DTO·권한 분기를 DB 없이 확인한다. 실제 DB 조건은 통합 테스트에서 검증한다. */
class AfterSalesServiceTest {
	private final AfterSalesRepository requests = mock(AfterSalesRepository.class);
	private final ContractRepository contracts = mock(ContractRepository.class);
	private final AfterSalesRequestImageRepository images = mock(AfterSalesRequestImageRepository.class);
	private final AfterSalesCommentRepository comments = mock(AfterSalesCommentRepository.class);
	private final AfterSalesImageProcessor processor = mock(AfterSalesImageProcessor.class);
	@SuppressWarnings("unchecked")
	private final ObjectProvider<AfterSalesImageStorage> provider = mock(ObjectProvider.class);
	private final AfterSalesImageStorage storage = mock(AfterSalesImageStorage.class);
	private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
	private AfterSalesService service;
	private final AfterSalesDto.CreateRequest request = new AfterSalesDto.CreateRequest(1L, " 누수 수리 ");

	@BeforeEach
	void setUp() {
		service = new AfterSalesService(requests, contracts, images, comments, processor, provider, 10);
		when(requests.save(any())).thenAnswer(call -> call.getArgument(0));
	}

	@AfterEach
	void clearSynchronization() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	private void contract(LocalDate start, LocalDate end) {
		when(contracts.findById(1L)).thenReturn(Optional.of(Contract.builder()
			.tenant(User.builder().userId(7L).build()).property(Property.builder().propertyId(3L).build())
			.startAt(start.atTime(23, 59)).endAt(end.atStartOfDay()).build()));
	}

	@Test
	void includesStartAndEndDatesAndAllowsNoPhotos() {
		contract(today, today);
		assertEquals(AsStatus.RECEIVED, service.createAfterSalesRequest(7L, request, null).getAsStatus());
		var captor = org.mockito.ArgumentCaptor.forClass(AfterSalesRequest.class);
		verify(requests).save(captor.capture());
		assertEquals(7L, captor.getValue().getTenant().getUserId());
		assertEquals(3L, captor.getValue().getProperty().getPropertyId());
		assertEquals("누수 수리", captor.getValue().getContent());
		verifyNoInteractions(provider, images);
	}

	@Test
	void rejectsOtherTenantBeforeSaving() {
		contract(today, today);
		assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(AfterSalesException.class,
			() -> service.createAfterSalesRequest(8L, request, null)).getErrorCode());
		verify(requests, never()).save(any());
	}

	@Test
	void rejectsBeforeStartAndAfterEnd() {
		for (int offset : List.of(-1, 1)) {
			contract(today.plusDays(offset), today.plusDays(offset));
			assertEquals(ErrorCode.AS_OUTSIDE_CONTRACT, assertThrows(AfterSalesException.class,
				() -> service.createAfterSalesRequest(7L, request, null)).getErrorCode());
		}
		verify(requests, never()).save(any());
	}

	@Test
	void rejectsMissingContract() {
		when(contracts.findById(1L)).thenReturn(Optional.empty());
		assertEquals(ErrorCode.AS_CONTRACT_NOT_FOUND, assertThrows(AfterSalesException.class,
			() -> service.createAfterSalesRequest(7L, request, null)).getErrorCode());
	}

	@Test
	void failsClosedWithoutStorage() {
		contract(today, today);
		assertEquals(ErrorCode.AS_STORAGE_UNAVAILABLE, assertThrows(AfterSalesException.class,
			() -> service.createAfterSalesRequest(7L, request, List.of(photo()))).getErrorCode());
		verify(requests, never()).save(any());
	}

	@Test
	void acceptsOnePhotoAndCleansUpOnCommitFailure() {
		contract(today, today);
		when(provider.getIfAvailable()).thenReturn(storage);
		when(processor.process(any())).thenReturn(new byte[]{1, 2});
		// 프록시 없는 단위 테스트이므로 동기화를 직접 열고 아래에서 롤백 완료 콜백을 실행한다.
		TransactionSynchronizationManager.initSynchronization();
		service.createAfterSalesRequest(7L, request, List.of(photo()));
		var key = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(storage).write(key.capture(), any());
		assertTrue(key.getValue().startsWith("as/7/"));
		verify(images).save(any());
		TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
		verify(storage).delete(key.getValue());
	}

	@Test
	void storageFailureDoesNotReturnSuccessAndCleansAttemptedKeys() {
		contract(today, today);
		when(provider.getIfAvailable()).thenReturn(storage);
		when(processor.process(any())).thenReturn(new byte[]{1});
		doThrow(new IllegalStateException("storage failed")).when(storage).write(any(), any());
		TransactionSynchronizationManager.initSynchronization();
		assertEquals(ErrorCode.AS_STORAGE_UNAVAILABLE, assertThrows(AfterSalesException.class,
			() -> service.createAfterSalesRequest(7L, request, List.of(photo()))).getErrorCode());
		TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
		verify(storage).delete(any());
		verifyNoInteractions(images);
	}

	@Test
	void readsOwnedRequestsWithPageAndSort() {
		var request = receivedRequest();
		var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
		when(requests.findAllByProperty_Landlord_UserId(eq(7L), pageable.capture()))
			.thenAnswer(call -> new PageImpl<>(List.of(request), call.getArgument(1), 6));

		var response = service.readReceivedAfterSalesRequests(7L, 2);

		assertEquals(1, pageable.getValue().getPageNumber());
		assertEquals(5, pageable.getValue().getPageSize());
		assertEquals(Sort.by(Sort.Direction.DESC, "createdAt", "asRequestId"), pageable.getValue().getSort());
		assertEquals(2, response.getPage());
		assertEquals(2, response.getTotalPages());
		assertEquals(6, response.getTotalElements());
		var item = response.getRequests().get(0);
		assertEquals(10L, item.getAsRequestId());
		assertEquals(3L, item.getPropertyId());
		assertEquals("서울특별시 마포구 테스트로 1", item.getRoadAddress());
		assertEquals("101호", item.getDetailAddress());
		assertEquals("임차인", item.getTenantName());
		assertEquals("누수 수리", item.getContent());
		assertEquals(AsStatus.CHECKING, item.getAsStatus());
		assertEquals(request.getCreatedAt(), item.getCreatedAt());
		verifyNoInteractions(contracts, images, provider, processor, storage);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1})
	void readsFirstPageWhenPageIsTooSmall(int page) {
		when(requests.findAllByProperty_Landlord_UserId(eq(7L), any()))
			.thenAnswer(call -> new PageImpl<>(List.of(), call.getArgument(1), 0));

		var response = service.readReceivedAfterSalesRequests(7L, page);

		assertEquals(1, response.getPage());
		assertTrue(response.getRequests().isEmpty());
		assertEquals(0, response.getTotalPages());
		assertEquals(0, response.getTotalElements());
	}

	@Test
	void readsDetailWithOnlyImageMetadataWithoutStorage() {
		var request = receivedRequest();
		when(requests.findByAsRequestIdAndProperty_Landlord_UserId(10L, 7L)).thenReturn(Optional.of(request));
		when(images.findAllByAsRequest_AsRequestIdOrderByCreatedAtAscAsRequestImageIdAsc(10L)).thenReturn(List.of(
			AfterSalesRequestImage.builder().asRequestImageId(20L).asRequest(request).fileKey("private/as/photo.jpg")
				.createdAt(request.getCreatedAt()).build()));

		var response = service.readAfterSalesRequest(7L, 10L);

		assertEquals(10L, response.getAsRequestId());
		assertEquals(3L, response.getPropertyId());
		assertEquals("임차인", response.getTenantName());
		assertEquals("누수 수리", response.getContent());
		assertEquals(AsStatus.CHECKING, response.getAsStatus());
		assertEquals(request.getUpdatedAt(), response.getUpdatedAt());
		assertEquals(20L, response.getImages().get(0).getAsRequestImageId());
		assertEquals(request.getCreatedAt(), response.getImages().get(0).getCreatedAt());
		verifyNoInteractions(contracts, provider, processor, storage);
	}

	@Test
	void readsDetailWithoutPhotos() {
		when(requests.findByAsRequestIdAndProperty_Landlord_UserId(10L, 7L))
			.thenReturn(Optional.of(receivedRequest()));
		when(images.findAllByAsRequest_AsRequestIdOrderByCreatedAtAscAsRequestImageIdAsc(10L)).thenReturn(List.of());

		assertTrue(service.readAfterSalesRequest(7L, 10L).getImages().isEmpty());
		verifyNoInteractions(provider, processor, storage);
	}

	@Test
	void rejectsUnavailableDetailBeforeReadingImages() {
		when(requests.findByAsRequestIdAndProperty_Landlord_UserId(10L, 7L)).thenReturn(Optional.empty());

		assertEquals(ErrorCode.AS_REQUEST_NOT_FOUND, assertThrows(AfterSalesException.class,
			() -> service.readAfterSalesRequest(7L, 10L)).getErrorCode());
		verify(requests, never()).findById(any());
		verifyNoInteractions(images, contracts, provider, processor, storage);
	}

	private AfterSalesRequest receivedRequest() {
		return AfterSalesRequest.builder().asRequestId(10L)
			.property(Property.builder().propertyId(3L).landlord(User.builder().userId(7L).build())
				.roadAddress("서울특별시 마포구 테스트로 1").detailAddress("101호").build())
			.tenant(User.builder().userId(8L).name("임차인").build()).content("누수 수리").asStatus(AsStatus.CHECKING)
			.createdAt(LocalDateTime.of(2026, 11, 6, 10, 0)).updatedAt(LocalDateTime.of(2026, 11, 6, 11, 0)).build();
	}

	private MockMultipartFile photo() {
		return new MockMultipartFile("images", "photo.png", "image/png", new byte[]{1});
	}
}
