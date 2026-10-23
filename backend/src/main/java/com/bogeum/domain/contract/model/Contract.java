package com.bogeum.domain.contract.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.bogeum.common.enums.LeaseType;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 임대차 계약. 중개사가 계약 정보를 올리면 만들어진다 (PROPERTY_011)
 */
@Entity
@Table(name = "contract")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contract {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long contractId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "property_id", nullable = false)
	private Property property;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "landlord_id", nullable = false)
	private User landlord;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tenant_id", nullable = false)
	private User tenant;

	// 중개 이력 조회의 기준 (AGENT_003)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "agent_id", nullable = false)
	private User agent;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private LeaseType leaseType;

	@Column(nullable = false, precision = 15, scale = 0)
	private BigDecimal deposit;

	// 전세는 0. 월세 자동 결제 금액과 같다 (RENT_004)
	@Builder.Default
	@Column(nullable = false, precision = 15, scale = 0)
	private BigDecimal monthlyRent = BigDecimal.ZERO;

	// 매월 며칠에 자동 결제하는지(1~31). 전세는 비어 있다 (RENT_004)
	@JdbcTypeCode(SqlTypes.TINYINT)
	private Integer paymentDay;

	// start_at ~ end_at 사이의 월세 납부 회차를 만든다
	@Column(nullable = false)
	private LocalDateTime startAt;

	@Column(nullable = false)
	private LocalDateTime endAt;

	// 계약 체결 시각. 입주 하자 사진 업로드 기한(체결 후 1주)의 기준 (MOVEIN_001)
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private LocalDateTime updatedAt;
}
