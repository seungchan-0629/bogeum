package com.bogeum.domain.property;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.domain.property.model.PropertyImage;

public interface PropertyImageRepository extends JpaRepository<PropertyImage, Long> {

	// 대표 사진(가장 작은 순서)이 맨 앞에 온다 (PROPERTY_004)
	List<PropertyImage> findAllByProperty_PropertyIdOrderBySortOrderAscPropertyImageIdAsc(Long propertyId);
}
