package com.bogeum.domain.property;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.domain.property.model.Property;

import jakarta.persistence.LockModeType;

// 매물 목록·검색은 받은 조건만 붙이는 Specification으로 조회한다 (PROPERTY_004, PROPERTY_005, PropertySpecifications)
public interface PropertyRepository extends JpaRepository<Property, Long>, JpaSpecificationExecutor<Property> {

	// 공개 중인 매물만 상세로 보여 준다. 담당 중개사 정보를 함께 보여 줘서 함께 가져온다 (PROPERTY_003)
	@EntityGraph(attributePaths = "agent")
	Optional<Property> findByPropertyIdAndPropertyStatus(Long propertyId, PropertyStatus propertyStatus);

	// 이 매물을 맡은 중개사 본인만 찾는다. 남의 매물은 없는 것과 같이 다룬다 (PROPERTY_010)
	@EntityGraph(attributePaths = "landlord")
	Optional<Property> findByPropertyIdAndAgent_UserId(Long propertyId, Long agentUserId);

	// 임대인 본인 매물만 찾는다. 중개사가 계약을 등록하는 중에 가격이 바뀌지 않도록 계약 등록과 같이 매물 행을 잠근다 (PROPERTY_006)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Property> findForUpdateByPropertyIdAndLandlord_UserId(Long propertyId, Long landlordUserId);

	// 사진 추가처럼 개수를 세고 늘리는 작업은 매물 행을 잠가 동시 요청이 상한을 넘지 않게 한다 (PROPERTY_010)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Property> findForUpdateByPropertyIdAndAgent_UserId(Long propertyId, Long agentUserId);
}
