package com.bogeum.domain.property;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.bogeum.domain.property.model.Property;

import jakarta.persistence.LockModeType;

public interface PropertyRepository extends JpaRepository<Property, Long> {

	// 이 매물을 맡은 중개사 본인만 찾는다. 남의 매물은 없는 것과 같이 다룬다 (PROPERTY_010)
	@EntityGraph(attributePaths = "landlord")
	Optional<Property> findByPropertyIdAndAgent_UserId(Long propertyId, Long agentUserId);

	// 사진 추가처럼 개수를 세고 늘리는 작업은 매물 행을 잠가 동시 요청이 상한을 넘지 않게 한다 (PROPERTY_010)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Property> findForUpdateByPropertyIdAndAgent_UserId(Long propertyId, Long agentUserId);
}
