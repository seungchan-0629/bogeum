package com.bogeum.domain.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.bogeum.common.enums.ApprovalStatus;
import com.bogeum.common.enums.BrokerageStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.PropertyType;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.agent.AgentRepository;
import com.bogeum.domain.agent.model.Agent;
import com.bogeum.domain.property.model.BrokerageRequest;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.property.model.PropertyDto;
import com.bogeum.domain.property.model.PropertyImage;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;

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

	private PropertyService propertyService;
	private final User landlord = User.builder().userId(1L).name("박임대").build();

	@BeforeEach
	void setUp() {
		propertyService = new PropertyService(userService, agentRepository, propertyRepository, brokerageRequestRepository,
			propertyImageRepository, storageProvider);
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

	private Property givenAssignedProperty() {
		Property property = Property.builder()
			.propertyId(20L).landlord(landlord).agent(User.builder().userId(101L).build())
			.leaseType(LeaseType.MONTHLY).deposit(BigDecimal.valueOf(10_000_000)).monthlyRent(BigDecimal.valueOf(700_000))
			.roadAddress(MAPO_ADDRESS).detailAddress("302호").propertyType(PropertyType.OFFICETEL).build();
		when(propertyRepository.findByPropertyIdAndAgent_UserId(20L, 101L)).thenReturn(Optional.of(property));
		return property;
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
