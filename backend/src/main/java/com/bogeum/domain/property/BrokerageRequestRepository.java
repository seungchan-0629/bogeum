package com.bogeum.domain.property;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.domain.property.model.BrokerageRequest;

public interface BrokerageRequestRepository extends JpaRepository<BrokerageRequest, Long> {
}
