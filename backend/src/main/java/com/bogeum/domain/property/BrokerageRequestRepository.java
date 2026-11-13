package com.bogeum.domain.property;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bogeum.common.enums.BrokerageStatus;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.domain.agent.model.Agent;
import com.bogeum.domain.property.model.BrokerageRequest;

public interface BrokerageRequestRepository extends JpaRepository<BrokerageRequest, Long> {

	// 임대인 본인이 중개를 신청한 매물 목록. 매물 하나에 신청이 하나라 신청 기준으로 매물과 신청 상태를 함께 본다.
	// 표에 매물 정보와 담당 중개사 이름을 보여 줘서 함께 가져온다. 삭제한 매물은 빼려고 매물 상태를 조건으로 건다 (PROPERTY_013)
	@EntityGraph(attributePaths = {"property", "agent"})
	Page<BrokerageRequest> findAllByLandlord_UserIdAndProperty_PropertyStatusNot(Long landlordUserId,
		PropertyStatus excludedStatus, Pageable pageable);

	// 임대인 본인의 매물 한 건과 그 중개 신청. 수정 화면 첫 값에 진행 상태까지 보여 줘서 신청 기준으로 찾는다 (PROPERTY_006)
	// 남의 매물·삭제한 매물은 없는 것과 같이 다룬다
	@EntityGraph(attributePaths = "property")
	Optional<BrokerageRequest> findByProperty_PropertyIdAndLandlord_UserIdAndProperty_PropertyStatusNot(Long propertyId,
		Long landlordUserId, PropertyStatus excludedStatus);

	// 목록에 나온 중개사들의 사무소 정보를 한 번에 가져온다. 매물마다 따로 조회하지 않게 한다 (PROPERTY_013)
	// 중개사 프로필 저장소(agent 패키지)를 고치지 않으려고, 목록을 만드는 이 저장소에 둔다
	@Query("SELECT a FROM Agent a WHERE a.user.userId IN :agentUserIds")
	List<Agent> findAgentProfilesByUserIds(@Param("agentUserIds") Collection<Long> agentUserIds);

	// 목록에 매물 정보와 임대인 이름을 보여 줘서 함께 가져온다 (PROPERTY_012)
	@EntityGraph(attributePaths = {"property", "landlord"})
	Page<BrokerageRequest> findAllByAgent_UserIdAndBrokerageStatus(Long agentUserId, BrokerageStatus brokerageStatus,
		Pageable pageable);

	// 중개사 본인에게 온 신청만 찾는다. 남의 신청은 없는 것과 같이 다룬다
	@EntityGraph(attributePaths = "property")
	Optional<BrokerageRequest> findByBrokerageRequestIdAndAgent_UserId(Long brokerageRequestId, Long agentUserId);
}
