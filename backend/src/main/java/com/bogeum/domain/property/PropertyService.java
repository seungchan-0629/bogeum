package com.bogeum.domain.property;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.common.enums.ApprovalStatus;
import com.bogeum.common.enums.BrokerageStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.domain.agent.AgentRepository;
import com.bogeum.domain.agent.model.Agent;
import com.bogeum.domain.property.model.BrokerageRequest;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.property.model.PropertyDto;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PropertyService {

	// 중개 신청 목록 한 페이지의 개수 (PROPERTY_012)
	private static final int PAGE_SIZE = 5;

	private final UserService userService;
	private final AgentRepository agentRepository;
	private final PropertyRepository propertyRepository;
	private final BrokerageRequestRepository brokerageRequestRepository;

	/**
	 * 매물을 만들고 중개사에게 중개를 신청한다 (PROPERTY_002).
	 * 매물의 중개사는 수락 전까지 비워 두고, 신청 건에만 배정된 중개사를 기록한다 (PROPERTY_008).
	 *
	 * @throws PropertyException 임대 형식과 가격이 맞지 않거나 신청할 중개사가 없는 경우
	 */
	@Transactional
	public PropertyDto.BrokerageResponse createBrokerageRequest(Long landlordUserId, PropertyDto.BrokerageRequest request) {
		User landlord = userService.findUser(landlordUserId);
		BigDecimal monthlyRent = validateLeasePrice(request.getLeaseType(), request.getDeposit(), request.getMonthlyRent());
		boolean isDesignated = request.getAgentId() != null;
		Agent agent = isDesignated ? findDesignatedAgent(request.getAgentId()) : pickAgent(request.getRoadAddress());

		Property property = propertyRepository.save(Property.builder()
			.landlord(landlord)
			.leaseType(request.getLeaseType())
			.deposit(request.getDeposit())
			.monthlyRent(monthlyRent)
			.roadAddress(request.getRoadAddress().trim())
			.detailAddress(request.getDetailAddress())
			.build());
		BrokerageRequest brokerageRequest = brokerageRequestRepository.save(BrokerageRequest.builder()
			.property(property)
			.landlord(landlord)
			.agent(agent.getUser())
			.isDesignated(isDesignated)
			.build());
		return PropertyDto.BrokerageResponse.from(brokerageRequest, agent);
	}

	/**
	 * 중개사 본인에게 온 중개 신청을 상태별로 페이지 단위로 조회한다 (PROPERTY_012).
	 * 새 신청은 신청한 순서, 수락한 신청은 수락한 순서로 최신이 위에 온다.
	 *
	 * @param page 1부터 센다. 1보다 작으면 첫 페이지를 준다
	 */
	@Transactional(readOnly = true)
	public PropertyDto.BrokerageListResponse readBrokerageRequests(Long agentUserId, BrokerageStatus status, int page) {
		String sortProperty = status == BrokerageStatus.ACCEPTED ? "acceptedAt" : "createdAt";
		Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, sortProperty));
		return PropertyDto.BrokerageListResponse.from(
			brokerageRequestRepository.findAllByAgent_UserIdAndBrokerageStatus(agentUserId, status, pageable));
	}

	/**
	 * 중개사가 본인에게 온 중개 신청을 수락하고 매물을 맡는다 (PROPERTY_008).
	 *
	 * @throws PropertyException 본인에게 온 신청이 아니거나 이미 수락한 경우
	 */
	@Transactional
	public PropertyDto.AcceptResponse acceptBrokerageRequest(Long agentUserId, Long brokerageRequestId) {
		BrokerageRequest brokerageRequest = brokerageRequestRepository
			.findByBrokerageRequestIdAndAgent_UserId(brokerageRequestId, agentUserId)
			.orElseThrow(PropertyException::brokerageRequestNotFound);
		brokerageRequest.accept();
		return PropertyDto.AcceptResponse.from(brokerageRequest);
	}

	/** 전세는 월세 없이 전세금만, 월세는 월세 금액이 있어야 한다. 저장할 월세 금액을 반환한다. */
	private BigDecimal validateLeasePrice(LeaseType leaseType, BigDecimal deposit, BigDecimal monthlyRent) {
		BigDecimal rent = monthlyRent == null ? BigDecimal.ZERO : monthlyRent;
		boolean isValid = leaseType == LeaseType.JEONSE
			? rent.signum() == 0 && deposit.signum() > 0
			: rent.signum() > 0;
		if (!isValid) {
			throw PropertyException.invalidLeasePrice();
		}
		return rent;
	}

	private Agent findDesignatedAgent(Long agentId) {
		return agentRepository.findByAgentProfileIdAndApprovalStatus(agentId, ApprovalStatus.APPROVED)
			.orElseThrow(PropertyException::agentNotAvailable);
	}

	/** 희망 중개사가 없으면 같은 지역 중개사 중에서, 없으면 승인된 중개사 전체에서 무작위로 고른다. */
	private Agent pickAgent(String roadAddress) {
		List<Agent> approvedAgents = agentRepository.findAllByApprovalStatus(ApprovalStatus.APPROVED);
		if (approvedAgents.isEmpty()) {
			throw PropertyException.noAgentAvailable();
		}
		List<Agent> localAgents = approvedAgents.stream()
			.filter(agent -> isSameRegion(agent.getOfficeAddress(), roadAddress))
			.toList();
		List<Agent> candidates = localAgents.isEmpty() ? approvedAgents : localAgents;
		return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
	}

	/**
	 * 시·도와 시·군·구가 같으면 같은 지역으로 본다.
	 * 주소 API마다 "서울특별시"·"서울"처럼 시·도 표기가 달라 시·도는 앞 두 글자만 비교한다.
	 */
	private boolean isSameRegion(String officeAddress, String roadAddress) {
		String[] office = officeAddress.trim().split("\\s+");
		String[] property = roadAddress.trim().split("\\s+");
		if (office.length < 2 || property.length < 2) {
			return false;
		}
		return regionPrefix(office[0]).equals(regionPrefix(property[0])) && office[1].equals(property[1]);
	}

	private String regionPrefix(String sido) {
		return sido.length() <= 2 ? sido : sido.substring(0, 2);
	}
}
