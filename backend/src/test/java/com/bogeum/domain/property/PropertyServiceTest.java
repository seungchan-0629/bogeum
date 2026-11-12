package com.bogeum.domain.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.bogeum.common.enums.ApprovalStatus;
import com.bogeum.common.enums.BrokerageStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.PropertyType;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.common.exception.ImageException;
import com.bogeum.domain.agent.AgentRepository;
import com.bogeum.domain.agent.model.Agent;
import com.bogeum.domain.property.model.BrokerageRequest;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.property.model.PropertyDto;
import com.bogeum.domain.property.model.PropertyImage;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

/** 중개사 배정과 가격 규칙을 DB 없이 확인한다. */
@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

	private static final String MAPO_ADDRESS = "서울특별시 마포구 월드컵로 61";

	@Mock
	private UserService userService;
	@Mock
	private AgentRepository agentRepository;
	@Mock
	private PropertyRepository propertyRepository;
	@Mock
	private BrokerageRequestRepository brokerageRequestRepository;
	@Mock
	private PropertyImageRepository propertyImageRepository;
	@Mock
	private ObjectProvider<PropertyImageStorage> storageProvider;
	@Mock
	private PropertyImageStorage storage;
	@Mock
	private ImageProcessor imageProcessor;

	private PropertyService propertyService;
	private final User landlord = User.builder().userId(1L).name("박임대").build();

	@BeforeEach
	void setUp() {
		propertyService = new PropertyService(userService, agentRepository, propertyRepository, brokerageRequestRepository,
			propertyImageRepository, storageProvider, imageProcessor);
	}

	@AfterEach
	void clearSynchronization() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	@DisplayName("사진을 추가하면 기존 사진 뒤 순서로 매물별 키에 저장하고 조회 주소를 붙여 돌려준다")
	void addsImagesAfterExistingImages() {
		Property property = givenAssignedPropertyForUpdate(PropertyStatus.REQUESTED);
		givenSavedImages(List.of(image(1L, 0, "property-images/20/a.jpg"), image(2L, 1, "property-images/20/b.jpg")));
		when(storageProvider.getIfAvailable()).thenReturn(storage);
		when(imageProcessor.process(any())).thenReturn(new byte[]{1});
		when(propertyImageRepository.save(any(PropertyImage.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(storage.createReadUrl(any())).thenAnswer(invocation -> "https://s3/" + invocation.getArgument(0));
		// 프록시 없는 단위 테스트라 트랜잭션 동기화를 직접 연다
		TransactionSynchronizationManager.initSynchronization();

		var response = propertyService.createPropertyImages(101L, 20L, List.of(photo(), photo()));

		var saved = ArgumentCaptor.forClass(PropertyImage.class);
		verify(propertyImageRepository, times(2)).save(saved.capture());
		assertEquals(List.of(2, 3), saved.getAllValues().stream().map(PropertyImage::getSortOrder).toList());
		assertTrue(saved.getAllValues().stream().allMatch(image -> image.getProperty() == property
			&& image.getFileKey().startsWith("property-images/20/") && image.getFileKey().endsWith(".jpg")));
		verify(storage, times(2)).write(any(), any());
		assertEquals(List.of(2, 3), response.stream().map(PropertyDto.ImageResponse::getSortOrder).toList());
		assertEquals("https://s3/" + saved.getAllValues().get(0).getFileKey(), response.get(0).getUrl());
	}

	@Test
	@DisplayName("사진이 없거나 기존 사진과 합쳐 10장을 넘으면 저장소에 올리지 않고 거절한다")
	void rejectsNoImagesOrOverLimit() {
		givenAssignedPropertyForUpdate(PropertyStatus.REQUESTED);
		givenSavedImages(java.util.stream.IntStream.range(0, 9)
			.mapToObj(index -> image((long) index, index, "property-images/20/" + index + ".jpg")).toList());

		for (List<MultipartFile> images : java.util.Arrays.<List<MultipartFile>>asList(
				null, List.of(), List.of(photo(), photo()))) {
			var exception = assertThrows(PropertyException.class,
				() -> propertyService.createPropertyImages(101L, 20L, images));
			assertEquals(ErrorCode.INVALID_IMAGE, exception.getErrorCode());
		}
		verifyNoInteractions(imageProcessor, storage);
	}

	@Test
	@DisplayName("저장소가 없으면 사진 추가를 성공으로 처리하지 않는다")
	void rejectsImagesWithoutStorage() {
		givenAssignedPropertyForUpdate(PropertyStatus.REQUESTED);
		givenSavedImages(List.of());
		when(storageProvider.getIfAvailable()).thenReturn(null);

		var exception = assertThrows(PropertyException.class,
			() -> propertyService.createPropertyImages(101L, 20L, List.of(photo())));

		assertEquals(ErrorCode.PROPERTY_STORAGE_UNAVAILABLE, exception.getErrorCode());
		verifyNoInteractions(imageProcessor);
		verify(propertyImageRepository, never()).save(any());
	}

	@Test
	@DisplayName("사진 하나라도 검사에 실패하면 저장소에 아무것도 올리지 않는다")
	void uploadsNothingWhenAnyImageIsInvalid() {
		givenAssignedPropertyForUpdate(PropertyStatus.REQUESTED);
		givenSavedImages(List.of());
		when(storageProvider.getIfAvailable()).thenReturn(storage);
		when(imageProcessor.process(any())).thenReturn(new byte[]{1}).thenThrow(ImageException.invalidImage());

		var exception = assertThrows(ImageException.class,
			() -> propertyService.createPropertyImages(101L, 20L, List.of(photo(), photo())));

		assertEquals(ErrorCode.INVALID_IMAGE, exception.getErrorCode());
		verifyNoInteractions(storage);
		verify(propertyImageRepository, never()).save(any());
	}

	@Test
	@DisplayName("저장 중 실패하면 저장 오류로 응답하고, 롤백되면 이번에 올린 파일을 지운다")
	void cleansUploadedImagesWhenStorageFails() {
		givenAssignedPropertyForUpdate(PropertyStatus.REQUESTED);
		givenSavedImages(List.of());
		when(storageProvider.getIfAvailable()).thenReturn(storage);
		when(imageProcessor.process(any())).thenReturn(new byte[]{1});
		when(propertyImageRepository.save(any(PropertyImage.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(storage.createReadUrl(any())).thenReturn("https://s3/a");
		doNothing().doThrow(new IllegalStateException("storage failed")).when(storage).write(any(), any());
		TransactionSynchronizationManager.initSynchronization();

		var exception = assertThrows(PropertyException.class,
			() -> propertyService.createPropertyImages(101L, 20L, List.of(photo(), photo())));
		TransactionSynchronizationManager.getSynchronizations()
			.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

		assertEquals(ErrorCode.PROPERTY_STORAGE_UNAVAILABLE, exception.getErrorCode());
		var written = ArgumentCaptor.forClass(String.class);
		verify(storage, times(2)).write(written.capture(), any());
		written.getAllValues().forEach(key -> verify(storage).delete(key));
	}

	@Test
	@DisplayName("공개 전이 아닌 매물에는 사진을 추가할 수 없다")
	void rejectsImagesForPublishedProperty() {
		givenAssignedPropertyForUpdate(PropertyStatus.ACTIVE);

		var exception = assertThrows(PropertyException.class,
			() -> propertyService.createPropertyImages(101L, 20L, List.of(photo())));

		assertEquals(ErrorCode.PROPERTY_NOT_EDITABLE, exception.getErrorCode());
		verifyNoInteractions(imageProcessor, storage);
	}

	@Test
	@DisplayName("없거나 다른 중개사가 맡은 매물에는 사진을 추가할 수 없다")
	void rejectsImagesForOthersProperty() {
		when(propertyRepository.findForUpdateByPropertyIdAndAgent_UserId(20L, 999L)).thenReturn(Optional.empty());

		var exception = assertThrows(PropertyException.class,
			() -> propertyService.createPropertyImages(999L, 20L, List.of(photo())));

		assertEquals(ErrorCode.PROPERTY_NOT_FOUND, exception.getErrorCode());
		verifyNoInteractions(propertyImageRepository, imageProcessor, storage);
	}

	private Property givenAssignedPropertyForUpdate(PropertyStatus propertyStatus) {
		Property property = Property.builder().propertyId(20L).landlord(landlord)
			.agent(User.builder().userId(101L).build()).propertyStatus(propertyStatus).build();
		when(propertyRepository.findForUpdateByPropertyIdAndAgent_UserId(20L, 101L)).thenReturn(Optional.of(property));
		return property;
	}

	private void givenSavedImages(List<PropertyImage> images) {
		when(propertyImageRepository.findAllByProperty_PropertyIdOrderBySortOrderAscPropertyImageIdAsc(20L))
			.thenReturn(images);
	}

	private static MockMultipartFile photo() {
		return new MockMultipartFile("images", "photo.png", "image/png", new byte[]{1});
	}

	@Test
	@DisplayName("희망 중개사를 지정하면 그 중개사에게 신청하고 매물의 중개사는 비워 둔다")
	void requestsToDesignatedAgent() {
		Agent agent = agent(3L, "서울특별시 강남구 도곡로 116");
		givenLandlord();
		givenSaves();
		when(agentRepository.findByAgentProfileIdAndApprovalStatus(3L, ApprovalStatus.APPROVED))
			.thenReturn(Optional.of(agent));

		var response = propertyService.createBrokerageRequest(1L, request(LeaseType.MONTHLY, 10_000_000, 700_000, 3L));

		BrokerageRequest saved = savedBrokerageRequest();
		assertTrue(saved.getIsDesignated());
		assertEquals(agent.getUser(), saved.getAgent());
		assertEquals(BrokerageStatus.REQUESTED, saved.getBrokerageStatus());
		assertNull(saved.getProperty().getAgent());
		assertEquals(PropertyStatus.REQUESTED, saved.getProperty().getPropertyStatus());
		assertEquals(3L, response.getAgentId());
		verify(agentRepository, never()).findAllByApprovalStatus(any());
	}

	@Test
	@DisplayName("없거나 승인되지 않은 중개사를 지정하면 저장하지 않고 거절한다")
	void rejectsUnavailableDesignatedAgent() {
		givenLandlord();
		when(agentRepository.findByAgentProfileIdAndApprovalStatus(9L, ApprovalStatus.APPROVED))
			.thenReturn(Optional.empty());

		var exception = assertThrows(PropertyException.class,
			() -> propertyService.createBrokerageRequest(1L, request(LeaseType.MONTHLY, 10_000_000, 700_000, 9L)));

		assertEquals(ErrorCode.BROKERAGE_AGENT_NOT_AVAILABLE, exception.getErrorCode());
		verify(propertyRepository, never()).save(any());
	}

	@RepeatedTest(10)
	@DisplayName("희망 중개사가 없으면 같은 시·군·구의 중개사를 배정한다 (시·도 표기가 달라도 같은 지역)")
	void picksLocalAgent() {
		Agent local = agent(1L, "서울 마포구 월드컵로 100");
		givenLandlord();
		givenSaves();
		when(agentRepository.findAllByApprovalStatus(ApprovalStatus.APPROVED)).thenReturn(List.of(
			agent(2L, "서울특별시 강남구 도곡로 116"), local, agent(3L, "부산광역시 마포구 가상로 1")));

		var response = propertyService.createBrokerageRequest(1L, request(LeaseType.MONTHLY, 10_000_000, 700_000, null));

		assertEquals(1L, response.getAgentId());
		assertFalse(savedBrokerageRequest().getIsDesignated());
	}

	@Test
	@DisplayName("같은 지역 중개사가 없으면 승인된 중개사 전체에서 배정한다")
	void picksAnyAgentWhenNoLocalAgent() {
		givenLandlord();
		givenSaves();
		when(agentRepository.findAllByApprovalStatus(ApprovalStatus.APPROVED))
			.thenReturn(List.of(agent(2L, "서울특별시 강남구 도곡로 116")));

		var response = propertyService.createBrokerageRequest(1L, request(LeaseType.JEONSE, 300_000_000, 0, null));

		assertEquals(2L, response.getAgentId());
	}

	@Test
	@DisplayName("승인된 중개사가 한 명도 없으면 거절한다")
	void rejectsWhenNoApprovedAgent() {
		givenLandlord();
		when(agentRepository.findAllByApprovalStatus(ApprovalStatus.APPROVED)).thenReturn(List.of());

		var exception = assertThrows(PropertyException.class,
			() -> propertyService.createBrokerageRequest(1L, request(LeaseType.MONTHLY, 10_000_000, 700_000, null)));

		assertEquals(ErrorCode.NO_BROKERAGE_AGENT, exception.getErrorCode());
		verify(propertyRepository, never()).save(any());
	}

	@Test
	@DisplayName("전세에 월세가 있거나, 전세금이 0이거나, 월세가 0이면 거절한다")
	void rejectsLeasePriceMismatch() {
		givenLandlord();
		for (var request : List.of(
				request(LeaseType.JEONSE, 300_000_000, 500_000, null),
				request(LeaseType.JEONSE, 0, 0, null),
				request(LeaseType.MONTHLY, 10_000_000, 0, null))) {
			var exception = assertThrows(PropertyException.class,
				() -> propertyService.createBrokerageRequest(1L, request));
			assertEquals(ErrorCode.INVALID_LEASE_PRICE, exception.getErrorCode());
		}
		verify(propertyRepository, never()).save(any());
	}

	@Test
	@DisplayName("전세는 월세를 비워 보내도 0원으로 저장한다")
	void storesZeroRentForJeonse() {
		givenLandlord();
		givenSaves();
		when(agentRepository.findAllByApprovalStatus(ApprovalStatus.APPROVED))
			.thenReturn(List.of(agent(2L, MAPO_ADDRESS)));

		propertyService.createBrokerageRequest(1L, new PropertyDto.BrokerageRequest(
			LeaseType.JEONSE, BigDecimal.valueOf(300_000_000), null, MAPO_ADDRESS, null, null));

		assertEquals(0, savedBrokerageRequest().getProperty().getMonthlyRent().signum());
	}

	@Test
	@DisplayName("중개사가 본인에게 온 신청을 수락하면 수락 상태가 되고 매물을 맡는다")
	void acceptsBrokerageRequest() {
		BrokerageRequest request = brokerageRequest(BrokerageStatus.REQUESTED);
		when(brokerageRequestRepository.findByBrokerageRequestIdAndAgent_UserId(10L, 101L)).thenReturn(Optional.of(request));

		var response = propertyService.acceptBrokerageRequest(101L, 10L);

		assertEquals(BrokerageStatus.ACCEPTED, request.getBrokerageStatus());
		assertNotNull(request.getAcceptedAt());
		assertEquals(request.getAgent(), request.getProperty().getAgent());
		assertEquals(PropertyStatus.REQUESTED, request.getProperty().getPropertyStatus());
		assertEquals(20L, response.getPropertyId());
		assertEquals(BrokerageStatus.ACCEPTED, response.getBrokerageStatus());
	}

	@Test
	@DisplayName("없거나 다른 중개사에게 온 신청은 수락할 수 없다")
	void rejectsAcceptingOthersRequest() {
		when(brokerageRequestRepository.findByBrokerageRequestIdAndAgent_UserId(10L, 999L)).thenReturn(Optional.empty());

		var exception = assertThrows(PropertyException.class, () -> propertyService.acceptBrokerageRequest(999L, 10L));

		assertEquals(ErrorCode.BROKERAGE_REQUEST_NOT_FOUND, exception.getErrorCode());
	}

	@Test
	@DisplayName("이미 수락한 신청은 다시 수락할 수 없다")
	void rejectsAcceptingTwice() {
		BrokerageRequest request = brokerageRequest(BrokerageStatus.ACCEPTED);
		when(brokerageRequestRepository.findByBrokerageRequestIdAndAgent_UserId(10L, 101L)).thenReturn(Optional.of(request));

		var exception = assertThrows(PropertyException.class, () -> propertyService.acceptBrokerageRequest(101L, 10L));

		assertEquals(ErrorCode.BROKERAGE_ALREADY_ACCEPTED, exception.getErrorCode());
	}

	@Test
	@DisplayName("새 신청은 신청 최신순으로, 한 페이지에 5개씩 본인에게 온 신청만 조회한다")
	void readsRequestedBrokerageRequests() {
		var pageable = givenBrokerageRequests(BrokerageStatus.REQUESTED, 12);

		var response = propertyService.readBrokerageRequests(101L, BrokerageStatus.REQUESTED, 1);

		assertEquals(0, pageable.getValue().getPageNumber());
		assertEquals(5, pageable.getValue().getPageSize());
		assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), pageable.getValue().getSort());
		assertEquals(1, response.getPage());
		assertEquals(3, response.getTotalPages());
		assertEquals(12, response.getTotalElements());
		assertEquals(20L, response.getRequests().get(0).getPropertyId());
		assertEquals(BrokerageStatus.REQUESTED, response.getRequests().get(0).getBrokerageStatus());
	}

	@Test
	@DisplayName("수락한 신청은 수락 최신순으로 조회한다")
	void readsAcceptedBrokerageRequestsByAcceptedAt() {
		var pageable = givenBrokerageRequests(BrokerageStatus.ACCEPTED, 1);

		propertyService.readBrokerageRequests(101L, BrokerageStatus.ACCEPTED, 1);

		assertEquals(Sort.by(Sort.Direction.DESC, "acceptedAt"), pageable.getValue().getSort());
	}

	@Test
	@DisplayName("1보다 작은 페이지를 요청하면 첫 페이지를 준다")
	void readsFirstPageWhenPageIsTooSmall() {
		var pageable = givenBrokerageRequests(BrokerageStatus.REQUESTED, 1);

		var response = propertyService.readBrokerageRequests(101L, BrokerageStatus.REQUESTED, 0);

		assertEquals(0, pageable.getValue().getPageNumber());
		assertEquals(1, response.getPage());
	}

	@Test
	@DisplayName("맡은 매물의 신청 조건·상세 정보와 대표 사진부터 조회 주소가 붙은 사진을 조회한다")
	void readsPropertyDetailWithReadUrl() {
		givenAssignedProperty();
		when(propertyImageRepository.findAllByProperty_PropertyIdOrderBySortOrderAscPropertyImageIdAsc(20L))
			.thenReturn(List.of(image(1L, 0, "property-images/20/a.jpg"), image(2L, 1, "property-images/20/b.jpg")));
		when(storageProvider.getIfAvailable()).thenReturn(storage);
		when(storage.createReadUrl("property-images/20/a.jpg")).thenReturn("https://s3/a");
		when(storage.createReadUrl("property-images/20/b.jpg")).thenReturn("https://s3/b");

		var response = propertyService.readPropertyDetail(101L, 20L);

		assertEquals(20L, response.getPropertyId());
		assertEquals("박임대", response.getLandlordName());
		assertEquals(LeaseType.MONTHLY, response.getLeaseType());
		assertEquals(PropertyType.OFFICETEL, response.getPropertyType());
		assertEquals(PropertyStatus.REQUESTED, response.getPropertyStatus());
		assertEquals(List.of(1L, 2L), response.getImages().stream().map(PropertyDto.ImageResponse::getPropertyImageId).toList());
		assertEquals(List.of("https://s3/a", "https://s3/b"),
			response.getImages().stream().map(PropertyDto.ImageResponse::getUrl).toList());
	}

	@Test
	@DisplayName("저장소가 없으면 사진 조회 주소 없이 상세를 조회한다")
	void readsPropertyDetailWithoutUrlWhenStorageIsUnavailable() {
		givenAssignedProperty();
		when(propertyImageRepository.findAllByProperty_PropertyIdOrderBySortOrderAscPropertyImageIdAsc(20L))
			.thenReturn(List.of(image(1L, 0, "property-images/20/a.jpg")));
		when(storageProvider.getIfAvailable()).thenReturn(null);

		var response = propertyService.readPropertyDetail(101L, 20L);

		assertNull(response.getImages().get(0).getUrl());
	}

	@Test
	@DisplayName("사진이 없으면 저장소를 찾지 않고 빈 사진 목록을 준다")
	void readsPropertyDetailWithoutImages() {
		givenAssignedProperty();
		when(propertyImageRepository.findAllByProperty_PropertyIdOrderBySortOrderAscPropertyImageIdAsc(20L))
			.thenReturn(List.of());

		var response = propertyService.readPropertyDetail(101L, 20L);

		assertTrue(response.getImages().isEmpty());
		verify(storageProvider, never()).getIfAvailable();
	}

	@Test
	@DisplayName("없거나 다른 중개사가 맡은 매물은 조회할 수 없다")
	void rejectsReadingOthersProperty() {
		when(propertyRepository.findByPropertyIdAndAgent_UserId(20L, 999L)).thenReturn(Optional.empty());

		var exception = assertThrows(PropertyException.class, () -> propertyService.readPropertyDetail(999L, 20L));

		assertEquals(ErrorCode.PROPERTY_NOT_FOUND, exception.getErrorCode());
		verify(propertyImageRepository, never()).findAllByProperty_PropertyIdOrderBySortOrderAscPropertyImageIdAsc(any());
	}

	@Test
	@DisplayName("상세 정보를 임시 저장하면 신청 조건과 공개 상태는 그대로 두고 상세 정보만 바꾼다")
	void savesPropertyDetail() {
		Property property = givenAssignedProperty();

		var response = propertyService.updatePropertyDetail(101L, 20L, new PropertyDto.DetailRequest(
			PropertyType.VILLA, new BigDecimal("17.32"), 3, 3, 5, 19, 10, "  대방역, 신대방역 "));

		assertEquals(PropertyType.VILLA, property.getPropertyType());
		assertEquals(new BigDecimal("17.32"), property.getAreaPyeong());
		assertEquals(3, property.getRoomCount());
		assertEquals(3, property.getFloor());
		assertEquals(5, property.getTotalFloor());
		assertEquals(19, property.getHouseholdCount());
		assertEquals(10, property.getParkingCount());
		assertEquals("대방역, 신대방역", property.getNearbySubwayStation());
		assertEquals(LeaseType.MONTHLY, property.getLeaseType());
		assertEquals(MAPO_ADDRESS, property.getRoadAddress());
		assertEquals(PropertyStatus.REQUESTED, response.getPropertyStatus());
		assertEquals(20L, response.getPropertyId());
	}

	@Test
	@DisplayName("임시 저장은 빈 값도 받아서 비운 채로 저장하고, 공백만 보낸 지하철역은 비운다")
	void savesEmptyPropertyDetail() {
		Property property = givenAssignedProperty();

		propertyService.updatePropertyDetail(101L, 20L, new PropertyDto.DetailRequest(
			null, null, null, null, null, null, null, "   "));

		assertNull(property.getPropertyType());
		assertNull(property.getRoomCount());
		assertNull(property.getNearbySubwayStation());
	}

	@Test
	@DisplayName("층이 전체 층보다 크면 저장하지 않고 거절한다")
	void rejectsFloorAboveTotalFloor() {
		Property property = givenAssignedProperty();

		var exception = assertThrows(PropertyException.class, () -> propertyService.updatePropertyDetail(101L, 20L,
			new PropertyDto.DetailRequest(null, null, null, 6, 5, null, null, null)));

		assertEquals(ErrorCode.INVALID_PROPERTY_FLOOR, exception.getErrorCode());
		assertNull(property.getFloor());
	}

	@Test
	@DisplayName("공개 전(REQUESTED)이 아닌 매물은 상세 정보를 저장할 수 없다")
	void rejectsSavingPublishedProperty() {
		when(propertyRepository.findByPropertyIdAndAgent_UserId(20L, 101L)).thenReturn(Optional.of(Property.builder()
			.propertyId(20L).landlord(landlord).propertyStatus(PropertyStatus.ACTIVE).build()));

		var exception = assertThrows(PropertyException.class, () -> propertyService.updatePropertyDetail(101L, 20L,
			new PropertyDto.DetailRequest(PropertyType.VILLA, null, null, null, null, null, null, null)));

		assertEquals(ErrorCode.PROPERTY_NOT_EDITABLE, exception.getErrorCode());
	}

	@Test
	@DisplayName("없거나 다른 중개사가 맡은 매물은 상세 정보를 저장할 수 없다")
	void rejectsSavingOthersProperty() {
		when(propertyRepository.findByPropertyIdAndAgent_UserId(20L, 999L)).thenReturn(Optional.empty());

		var exception = assertThrows(PropertyException.class, () -> propertyService.updatePropertyDetail(999L, 20L,
			new PropertyDto.DetailRequest(PropertyType.VILLA, null, null, null, null, null, null, null)));

		assertEquals(ErrorCode.PROPERTY_NOT_FOUND, exception.getErrorCode());
	}

	@Test
	@DisplayName("매물 목록 첫 화면은 공개 중인 매물을 12개씩 주고, 매물마다 순서가 가장 앞선 사진을 대표 사진으로 준다")
	void readsFirstPublishedProperties() {
		Property first = publishedProperty(31L, LocalDateTime.of(2026, 11, 12, 10, 0));
		Property second = publishedProperty(30L, LocalDateTime.of(2026, 11, 11, 9, 0));
		var pageable = ArgumentCaptor.forClass(Pageable.class);
		when(propertyRepository.findPublished(eq(PropertyStatus.ACTIVE), pageable.capture()))
			.thenReturn(new SliceImpl<>(List.of(first, second), PageRequest.of(0, 12), true));
		when(propertyImageRepository.findAllByProperty_PropertyIdInOrderBySortOrderAscPropertyImageIdAsc(List.of(31L, 30L)))
			.thenReturn(List.of(listImage(first, "property-images/31/a.jpg"), listImage(second, "property-images/30/a.jpg"),
				listImage(first, "property-images/31/b.jpg")));
		when(storageProvider.getIfAvailable()).thenReturn(storage);
		when(storage.createReadUrl(any())).thenAnswer(invocation -> "https://s3/" + invocation.getArgument(0));

		var response = propertyService.readProperties(null, null);

		assertEquals(0, pageable.getValue().getPageNumber());
		assertEquals(12, pageable.getValue().getPageSize());
		assertEquals(List.of(31L, 30L),
			response.getProperties().stream().map(PropertyDto.PropertyItemResponse::getPropertyId).toList());
		var item = response.getProperties().get(0);
		assertEquals("https://s3/property-images/31/a.jpg", item.getThumbnailUrl());
		assertEquals(PropertyType.OFFICETEL, item.getPropertyType());
		assertEquals(LeaseType.MONTHLY, item.getLeaseType());
		assertEquals(BigDecimal.valueOf(10_000_000), item.getDeposit());
		assertEquals(BigDecimal.valueOf(700_000), item.getMonthlyRent());
		assertEquals(new BigDecimal("8.50"), item.getAreaPyeong());
		assertEquals(MAPO_ADDRESS, item.getRoadAddress());
		assertEquals("월드컵경기장역", item.getNearbySubwayStation());
		assertEquals("https://s3/property-images/30/a.jpg", response.getProperties().get(1).getThumbnailUrl());
		assertTrue(response.isHasNext());
		assertEquals(LocalDateTime.of(2026, 11, 11, 9, 0), response.getNextCursor().getCursorPublishedAt());
		assertEquals(30L, response.getNextCursor().getCursorPropertyId());
	}

	@Test
	@DisplayName("커서를 보내면 그 매물 다음부터 조회하고, 마지막 목록이면 다음 커서를 비운다")
	void readsPublishedPropertiesAfterCursor() {
		LocalDateTime cursorPublishedAt = LocalDateTime.of(2026, 11, 11, 9, 0);
		when(propertyRepository.findPublishedAfter(eq(PropertyStatus.ACTIVE), eq(cursorPublishedAt), eq(30L), any()))
			.thenReturn(new SliceImpl<>(List.of(publishedProperty(29L, cursorPublishedAt)), PageRequest.of(0, 12), false));

		var response = propertyService.readProperties(cursorPublishedAt, 30L);

		assertEquals(29L, response.getProperties().get(0).getPropertyId());
		assertFalse(response.isHasNext());
		assertNull(response.getNextCursor());
		verify(propertyRepository, never()).findPublished(any(), any());
	}

	@Test
	@DisplayName("사진이 없거나 저장소가 없으면 대표 사진만 비우고 목록은 준다")
	void readsPropertiesWithoutThumbnail() {
		Property withImage = publishedProperty(31L, LocalDateTime.of(2026, 11, 12, 10, 0));
		Property withoutImage = publishedProperty(30L, LocalDateTime.of(2026, 11, 11, 9, 0));
		when(propertyRepository.findPublished(eq(PropertyStatus.ACTIVE), any()))
			.thenReturn(new SliceImpl<>(List.of(withImage, withoutImage), PageRequest.of(0, 12), false));
		when(propertyImageRepository.findAllByProperty_PropertyIdInOrderBySortOrderAscPropertyImageIdAsc(List.of(31L, 30L)))
			.thenReturn(List.of(listImage(withImage, "property-images/31/a.jpg")));
		when(storageProvider.getIfAvailable()).thenReturn(null);

		var response = propertyService.readProperties(null, null);

		assertEquals(2, response.getProperties().size());
		assertTrue(response.getProperties().stream().allMatch(item -> item.getThumbnailUrl() == null));
	}

	@Test
	@DisplayName("공개 중인 매물이 없으면 사진을 조회하지 않고 빈 목록을 준다")
	void readsEmptyProperties() {
		when(propertyRepository.findPublished(eq(PropertyStatus.ACTIVE), any()))
			.thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 12), false));

		var response = propertyService.readProperties(null, null);

		assertEquals(List.of(), response.getProperties());
		assertNull(response.getNextCursor());
		verifyNoInteractions(propertyImageRepository, storageProvider);
	}

	@Test
	@DisplayName("커서의 공개 시각과 매물 번호 중 하나만 오면 조회하지 않고 거절한다")
	void rejectsHalfCursor() {
		var withoutPropertyId = assertThrows(PropertyException.class,
			() -> propertyService.readProperties(LocalDateTime.of(2026, 11, 11, 9, 0), null));
		var withoutPublishedAt = assertThrows(PropertyException.class, () -> propertyService.readProperties(null, 30L));

		assertEquals(ErrorCode.INVALID_REQUEST, withoutPropertyId.getErrorCode());
		assertEquals(ErrorCode.INVALID_REQUEST, withoutPublishedAt.getErrorCode());
		verifyNoInteractions(propertyRepository);
	}

	@Test
	@DisplayName("필수 상세 정보와 사진이 있으면 매물을 공개(ACTIVE)한다")
	void publishesProperty() {
		Property property = completeProperty().build();
		when(propertyRepository.findForUpdateByPropertyIdAndAgent_UserId(20L, 101L)).thenReturn(Optional.of(property));
		when(propertyImageRepository.existsByProperty_PropertyId(20L)).thenReturn(true);

		var response = propertyService.publishProperty(101L, 20L);

		assertEquals(PropertyStatus.ACTIVE, property.getPropertyStatus());
		// 매물 목록은 공개한 시각 순서로 보여 준다 (PROPERTY_004)
		assertNotNull(property.getPublishedAt());
		assertEquals(20L, response.getPropertyId());
		assertEquals(PropertyStatus.ACTIVE, response.getPropertyStatus());
	}

	@Test
	@DisplayName("세대수·주차 대수·지하철역은 비어 있어도 공개한다")
	void publishesPropertyWithoutOptionalDetails() {
		Property property = completeProperty().householdCount(null).parkingCount(null).nearbySubwayStation(null).build();
		when(propertyRepository.findForUpdateByPropertyIdAndAgent_UserId(20L, 101L)).thenReturn(Optional.of(property));
		when(propertyImageRepository.existsByProperty_PropertyId(20L)).thenReturn(true);

		propertyService.publishProperty(101L, 20L);

		assertEquals(PropertyStatus.ACTIVE, property.getPropertyStatus());
	}

	@Test
	@DisplayName("필수 상세 정보가 하나라도 없으면 공개하지 않는다")
	void rejectsPublishingWithoutRequiredDetails() {
		for (Property property : List.of(
				completeProperty().propertyType(null).build(),
				completeProperty().areaPyeong(null).build(),
				completeProperty().roomCount(null).build(),
				completeProperty().floor(null).build(),
				completeProperty().totalFloor(null).build())) {
			when(propertyRepository.findForUpdateByPropertyIdAndAgent_UserId(20L, 101L)).thenReturn(Optional.of(property));
			when(propertyImageRepository.existsByProperty_PropertyId(20L)).thenReturn(true);

			var exception = assertThrows(PropertyException.class, () -> propertyService.publishProperty(101L, 20L));

			assertEquals(ErrorCode.PROPERTY_NOT_PUBLISHABLE, exception.getErrorCode());
			assertEquals(PropertyStatus.REQUESTED, property.getPropertyStatus());
		}
	}

	@Test
	@DisplayName("사진이 없으면 공개하지 않는다")
	void rejectsPublishingWithoutImage() {
		Property property = completeProperty().build();
		when(propertyRepository.findForUpdateByPropertyIdAndAgent_UserId(20L, 101L)).thenReturn(Optional.of(property));
		when(propertyImageRepository.existsByProperty_PropertyId(20L)).thenReturn(false);

		var exception = assertThrows(PropertyException.class, () -> propertyService.publishProperty(101L, 20L));

		assertEquals(ErrorCode.PROPERTY_NOT_PUBLISHABLE, exception.getErrorCode());
		assertEquals(PropertyStatus.REQUESTED, property.getPropertyStatus());
	}

	@Test
	@DisplayName("이미 공개됐거나 공개 전이 아닌 매물은 다시 공개할 수 없다")
	void rejectsPublishingPublishedProperty() {
		when(propertyRepository.findForUpdateByPropertyIdAndAgent_UserId(20L, 101L))
			.thenReturn(Optional.of(completeProperty().propertyStatus(PropertyStatus.ACTIVE).build()));

		var exception = assertThrows(PropertyException.class, () -> propertyService.publishProperty(101L, 20L));

		assertEquals(ErrorCode.PROPERTY_NOT_EDITABLE, exception.getErrorCode());
	}

	@Test
	@DisplayName("없거나 다른 중개사가 맡은 매물은 공개할 수 없다")
	void rejectsPublishingOthersProperty() {
		when(propertyRepository.findForUpdateByPropertyIdAndAgent_UserId(20L, 999L)).thenReturn(Optional.empty());

		var exception = assertThrows(PropertyException.class, () -> propertyService.publishProperty(999L, 20L));

		assertEquals(ErrorCode.PROPERTY_NOT_FOUND, exception.getErrorCode());
		verifyNoInteractions(propertyImageRepository);
	}

	// 공개에 필요한 상세 정보를 모두 채운 공개 전 매물
	private Property.PropertyBuilder completeProperty() {
		return Property.builder().propertyId(20L).landlord(landlord).agent(User.builder().userId(101L).build())
			.propertyType(PropertyType.VILLA).areaPyeong(BigDecimal.valueOf(17.32)).roomCount(3).floor(-1).totalFloor(5)
			.householdCount(19).parkingCount(0).nearbySubwayStation("대방역");
	}

	@Test
	@DisplayName("사진을 지우면 DB에서 먼저 지우고, 저장소 파일은 커밋된 뒤에만 지운다")
	void deletesImageFileAfterCommit() {
		givenAssignedPropertyForUpdate(PropertyStatus.REQUESTED);
		PropertyImage image = givenImage(5L, "property-images/20/a.jpg");
		when(storageProvider.getIfAvailable()).thenReturn(storage);
		TransactionSynchronizationManager.initSynchronization();

		propertyService.deletePropertyImage(101L, 20L, 5L);

		verify(propertyImageRepository).delete(image);
		verify(storage, never()).delete(any());
		TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
		verify(storage).delete("property-images/20/a.jpg");
	}

	@Test
	@DisplayName("롤백되면 저장소 파일을 지우지 않는다")
	void keepsImageFileWhenRolledBack() {
		givenAssignedPropertyForUpdate(PropertyStatus.REQUESTED);
		givenImage(5L, "property-images/20/a.jpg");
		when(storageProvider.getIfAvailable()).thenReturn(storage);
		TransactionSynchronizationManager.initSynchronization();

		propertyService.deletePropertyImage(101L, 20L, 5L);
		TransactionSynchronizationManager.getSynchronizations()
			.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

		verifyNoInteractions(storage);
	}

	@Test
	@DisplayName("커밋 뒤 저장소 파일 삭제에 실패해도 사진 삭제는 실패로 바꾸지 않는다")
	void ignoresStorageFailureAfterCommit() {
		givenAssignedPropertyForUpdate(PropertyStatus.REQUESTED);
		givenImage(5L, "property-images/20/a.jpg");
		when(storageProvider.getIfAvailable()).thenReturn(storage);
		doThrow(new IllegalStateException("storage failed")).when(storage).delete(any());
		TransactionSynchronizationManager.initSynchronization();

		propertyService.deletePropertyImage(101L, 20L, 5L);

		TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
		verify(storage).delete("property-images/20/a.jpg");
	}

	@Test
	@DisplayName("없거나 그 매물의 사진이 아니면 지우지 않고 거절한다")
	void rejectsDeletingOtherPropertysImage() {
		givenAssignedPropertyForUpdate(PropertyStatus.REQUESTED);
		when(propertyImageRepository.findByPropertyImageIdAndProperty_PropertyId(77L, 20L)).thenReturn(Optional.empty());

		var exception = assertThrows(PropertyException.class, () -> propertyService.deletePropertyImage(101L, 20L, 77L));

		assertEquals(ErrorCode.PROPERTY_IMAGE_NOT_FOUND, exception.getErrorCode());
		verify(propertyImageRepository, never()).delete(any());
		verifyNoInteractions(storage);
	}

	@Test
	@DisplayName("저장소가 없으면 파일이 남지 않도록 사진을 지우지 않는다")
	void rejectsDeletingImageWithoutStorage() {
		givenAssignedPropertyForUpdate(PropertyStatus.REQUESTED);
		givenImage(5L, "property-images/20/a.jpg");
		when(storageProvider.getIfAvailable()).thenReturn(null);

		var exception = assertThrows(PropertyException.class, () -> propertyService.deletePropertyImage(101L, 20L, 5L));

		assertEquals(ErrorCode.PROPERTY_STORAGE_UNAVAILABLE, exception.getErrorCode());
		verify(propertyImageRepository, never()).delete(any());
	}

	@Test
	@DisplayName("공개 전이 아닌 매물의 사진은 지울 수 없다")
	void rejectsDeletingImageOfPublishedProperty() {
		givenAssignedPropertyForUpdate(PropertyStatus.ACTIVE);

		var exception = assertThrows(PropertyException.class, () -> propertyService.deletePropertyImage(101L, 20L, 5L));

		assertEquals(ErrorCode.PROPERTY_NOT_EDITABLE, exception.getErrorCode());
		verifyNoInteractions(propertyImageRepository, storage);
	}

	@Test
	@DisplayName("없거나 다른 중개사가 맡은 매물의 사진은 지울 수 없다")
	void rejectsDeletingImageOfOthersProperty() {
		when(propertyRepository.findForUpdateByPropertyIdAndAgent_UserId(20L, 999L)).thenReturn(Optional.empty());

		var exception = assertThrows(PropertyException.class, () -> propertyService.deletePropertyImage(999L, 20L, 5L));

		assertEquals(ErrorCode.PROPERTY_NOT_FOUND, exception.getErrorCode());
		verifyNoInteractions(propertyImageRepository, storage);
	}

	private PropertyImage givenImage(Long propertyImageId, String fileKey) {
		PropertyImage image = image(propertyImageId, 0, fileKey);
		when(propertyImageRepository.findByPropertyImageIdAndProperty_PropertyId(propertyImageId, 20L))
			.thenReturn(Optional.of(image));
		return image;
	}

	private Property givenAssignedProperty() {
		Property property = Property.builder()
			.propertyId(20L).landlord(landlord).agent(User.builder().userId(101L).build())
			.leaseType(LeaseType.MONTHLY).deposit(BigDecimal.valueOf(10_000_000)).monthlyRent(BigDecimal.valueOf(700_000))
			.roadAddress(MAPO_ADDRESS).detailAddress("302호").propertyType(PropertyType.OFFICETEL).build();
		when(propertyRepository.findByPropertyIdAndAgent_UserId(20L, 101L)).thenReturn(Optional.of(property));
		return property;
	}

	private static Property publishedProperty(Long propertyId, LocalDateTime publishedAt) {
		return Property.builder().propertyId(propertyId).propertyType(PropertyType.OFFICETEL).leaseType(LeaseType.MONTHLY)
			.deposit(BigDecimal.valueOf(10_000_000)).monthlyRent(BigDecimal.valueOf(700_000))
			.areaPyeong(new BigDecimal("8.50")).roadAddress(MAPO_ADDRESS).detailAddress("302호")
			.nearbySubwayStation("월드컵경기장역").propertyStatus(PropertyStatus.ACTIVE).publishedAt(publishedAt).build();
	}

	private static PropertyImage listImage(Property property, String fileKey) {
		return PropertyImage.builder().property(property).fileKey(fileKey).build();
	}

	private static PropertyImage image(Long propertyImageId, int sortOrder, String fileKey) {
		return PropertyImage.builder().propertyImageId(propertyImageId).sortOrder(sortOrder).fileKey(fileKey).build();
	}

	// 실제 조회 결과처럼 요청받은 페이지와 전체 개수로 Page를 만들어 돌려준다
	private ArgumentCaptor<Pageable> givenBrokerageRequests(BrokerageStatus brokerageStatus, long totalElements) {
		var pageable = ArgumentCaptor.forClass(Pageable.class);
		when(brokerageRequestRepository.findAllByAgent_UserIdAndBrokerageStatus(eq(101L), eq(brokerageStatus),
				pageable.capture()))
			.thenAnswer(invocation -> new PageImpl<>(List.of(brokerageRequest(brokerageStatus)),
				invocation.getArgument(2), totalElements));
		return pageable;
	}

	private BrokerageRequest brokerageRequest(BrokerageStatus brokerageStatus) {
		return BrokerageRequest.builder().brokerageRequestId(10L)
			.property(Property.builder().propertyId(20L).landlord(landlord).build())
			.landlord(landlord).agent(User.builder().userId(101L).build())
			.brokerageStatus(brokerageStatus).build();
	}

	private void givenLandlord() {
		when(userService.findUser(1L)).thenReturn(landlord);
	}

	private void givenSaves() {
		when(propertyRepository.save(any(Property.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(brokerageRequestRepository.save(any(BrokerageRequest.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
	}

	private BrokerageRequest savedBrokerageRequest() {
		var captor = ArgumentCaptor.forClass(BrokerageRequest.class);
		verify(brokerageRequestRepository).save(captor.capture());
		return captor.getValue();
	}

	private static Agent agent(Long agentId, String officeAddress) {
		return Agent.builder().agentProfileId(agentId).user(User.builder().userId(100L + agentId).build())
			.officeName("중개사무소" + agentId).officeAddress(officeAddress)
			.approvalStatus(ApprovalStatus.APPROVED).build();
	}

	private static PropertyDto.BrokerageRequest request(LeaseType leaseType, long deposit, long monthlyRent, Long agentId) {
		return new PropertyDto.BrokerageRequest(leaseType, BigDecimal.valueOf(deposit), BigDecimal.valueOf(monthlyRent),
			MAPO_ADDRESS, "302호", agentId);
	}
}
