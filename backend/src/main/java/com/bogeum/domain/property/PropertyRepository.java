package com.bogeum.domain.property;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.domain.property.model.Property;

import jakarta.persistence.LockModeType;

public interface PropertyRepository extends JpaRepository<Property, Long> {

	// 매물 목록 첫 화면. 최근에 공개된 매물부터, 같은 시각이면 번호가 큰 매물부터 준다 (PROPERTY_004)
	@Query("SELECT p FROM Property p WHERE p.propertyStatus = :status "
		+ "ORDER BY p.publishedAt DESC, p.propertyId DESC")
	Slice<Property> findPublished(@Param("status") PropertyStatus status, Pageable pageable);

	// 앞 목록의 마지막 매물(커서) 다음부터 같은 순서로 이어서 준다. 스크롤 중 새 매물이 공개돼도 겹치거나 빠지지 않는다 (PROPERTY_004)
	@Query("SELECT p FROM Property p WHERE p.propertyStatus = :status AND (p.publishedAt < :publishedAt "
		+ "OR (p.publishedAt = :publishedAt AND p.propertyId < :propertyId)) "
		+ "ORDER BY p.publishedAt DESC, p.propertyId DESC")
	Slice<Property> findPublishedAfter(@Param("status") PropertyStatus status,
		@Param("publishedAt") LocalDateTime publishedAt, @Param("propertyId") Long propertyId, Pageable pageable);

	// 이 매물을 맡은 중개사 본인만 찾는다. 남의 매물은 없는 것과 같이 다룬다 (PROPERTY_010)
	@EntityGraph(attributePaths = "landlord")
	Optional<Property> findByPropertyIdAndAgent_UserId(Long propertyId, Long agentUserId);

	// 사진 추가처럼 개수를 세고 늘리는 작업은 매물 행을 잠가 동시 요청이 상한을 넘지 않게 한다 (PROPERTY_010)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Property> findForUpdateByPropertyIdAndAgent_UserId(Long propertyId, Long agentUserId);
}
