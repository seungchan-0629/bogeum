package com.bogeum.domain.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

import com.bogeum.common.enums.ApprovalStatus;
import com.bogeum.common.enums.BrokerageStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.agent.AgentRepository;
import com.bogeum.domain.agent.model.Agent;
import com.bogeum.domain.property.model.BrokerageRequest;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.property.model.PropertyDto;
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

	private PropertyService propertyService;
	private final User landlord = User.builder().userId(1L).build();

	@BeforeEach
	void setUp() {
		propertyService = new PropertyService(userService, agentRepository, propertyRepository, brokerageRequestRepository);
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
