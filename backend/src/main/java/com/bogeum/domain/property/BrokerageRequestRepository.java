package com.bogeum.domain.property;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.domain.property.model.BrokerageRequest;

public interface BrokerageRequestRepository extends JpaRepository<BrokerageRequest, Long> {

	// 중개사 본인에게 온 신청만 찾는다. 남의 신청은 없는 것과 같이 다룬다
	@EntityGraph(attributePaths = "property")
	Optional<BrokerageRequest> findByBrokerageRequestIdAndAgent_UserId(Long brokerageRequestId, Long agentUserId);
}
