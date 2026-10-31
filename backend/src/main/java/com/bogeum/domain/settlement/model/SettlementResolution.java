package com.bogeum.domain.settlement.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 은행 별도 증빙을 근거로 관리자가 원거래를 확정한 감사 기록. 자동 은행 조회 결과가 아니다. */
@Entity
@Table(name = "settlement_resolution")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementResolution {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long settlementResolutionId;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "settlement_attempt_id", nullable = false, unique = true, updatable = false)
	private SettlementAttempt settlementAttempt;

	@Column(nullable = false, updatable = false)
	private Long adminUserId;

	@Column(nullable = false, length = 500, updatable = false)
	private String evidenceReference;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20, updatable = false)
	private SettlementAttempt.Status previousStatus;

	@Column(nullable = false, updatable = false)
	private boolean succeeded;

	@Column(nullable = false, updatable = false)
	private LocalDateTime resolvedAt;
}
