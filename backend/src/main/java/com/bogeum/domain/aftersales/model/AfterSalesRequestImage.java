package com.bogeum.domain.aftersales.model;

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
 * AS 요청 사진 (AS_001)
 */
@Entity
@Table(name = "as_request_image")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSalesRequestImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long asRequestImageId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "as_request_id", nullable = false)
	private AfterSalesRequest asRequest;

	@Column(nullable = false, length = 500)
	private String fileKey;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
