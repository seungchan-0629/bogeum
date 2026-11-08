package com.bogeum.domain.property;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.domain.property.model.PropertyImage;

public interface PropertyImageRepository extends JpaRepository<PropertyImage, Long> {

	// 대표 사진(가장 작은 순서)이 맨 앞에 온다 (PROPERTY_004)
	List<PropertyImage> findAllByProperty_PropertyIdOrderBySortOrderAscPropertyImageIdAsc(Long propertyId);

	// 다른 매물의 사진 번호로 지우지 못하도록 매물 조건을 함께 건다 (PROPERTY_010)
	Optional<PropertyImage> findByPropertyImageIdAndProperty_PropertyId(Long propertyImageId, Long propertyId);
}
