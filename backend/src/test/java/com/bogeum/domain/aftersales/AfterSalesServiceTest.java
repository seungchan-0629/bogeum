package com.bogeum.domain.aftersales;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.bogeum.common.enums.AsStatus;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.aftersales.model.AfterSalesDto;
import com.bogeum.domain.aftersales.model.AfterSalesRequest;
import com.bogeum.domain.contract.ContractRepository;
import com.bogeum.domain.contract.model.Contract;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.model.User;

/** 계약·기간·저장소 분기를 DB 없이 확인한다. 실제 파일 저장과 DB 트랜잭션 검증은 포함하지 않는다. */
class AfterSalesServiceTest {
	private final AfterSalesRepository requests = mock(AfterSalesRepository.class);
	private final ContractRepository contracts = mock(ContractRepository.class);
	private final AfterSalesRequestImageRepository images = mock(AfterSalesRequestImageRepository.class);
	private final AfterSalesImageProcessor processor = mock(AfterSalesImageProcessor.class);
	@SuppressWarnings("unchecked")
	private final ObjectProvider<AfterSalesImageStorage> provider = mock(ObjectProvider.class);
	private final AfterSalesImageStorage storage = mock(AfterSalesImageStorage.class);
	private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
	private AfterSalesService service;
	private final AfterSalesDto.CreateRequest request = new AfterSalesDto.CreateRequest(1L, " 누수 수리 ");

	@BeforeEach
	void setUp() {
		service = new AfterSalesService(requests, contracts, images, processor, provider, 10);
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

	private MockMultipartFile photo() {
		return new MockMultipartFile("images", "photo.png", "image/png", new byte[]{1});
	}
}
