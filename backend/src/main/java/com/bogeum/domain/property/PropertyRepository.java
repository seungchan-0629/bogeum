package com.bogeum.domain.property;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.domain.property.model.Property;

public interface PropertyRepository extends JpaRepository<Property, Long> {

	// 이 매물을 맡은 중개사 본인만 찾는다. 남의 매물은 없는 것과 같이 다룬다 (PROPERTY_010)
	@EntityGraph(attributePaths = "landlord")
	Optional<Property> findByPropertyIdAndAgent_UserId(Long propertyId, Long agentUserId);
}
