package com.bogeum.domain.property.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매물 사진 (PROPERTY_010)
 */
@Entity
@Table(name = "property_image")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long propertyImageId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "property_id", nullable = false)
	private Property property;

	@Column(nullable = false, length = 500)
	private String fileKey;

	// 작을수록 앞에 보이고, 가장 작은 것이 대표 사진이다 (PROPERTY_004)
	@Builder.Default
	@Column(nullable = false)
	private Integer sortOrder = 0;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
