package com.bogeum.domain.property;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.common.enums.BrokerageStatus;
import com.bogeum.domain.property.model.BrokerageRequest;

public interface BrokerageRequestRepository extends JpaRepository<BrokerageRequest, Long> {

	// 목록에 매물 정보와 임대인 이름을 보여 줘서 함께 가져온다 (PROPERTY_012)
	@EntityGraph(attributePaths = {"property", "landlord"})
	Page<BrokerageRequest> findAllByAgent_UserIdAndBrokerageStatus(Long agentUserId, BrokerageStatus brokerageStatus,
		Pageable pageable);

	// 중개사 본인에게 온 신청만 찾는다. 남의 신청은 없는 것과 같이 다룬다
	@EntityGraph(attributePaths = "property")
	Optional<BrokerageRequest> findByBrokerageRequestIdAndAgent_UserId(Long brokerageRequestId, Long agentUserId);
}
