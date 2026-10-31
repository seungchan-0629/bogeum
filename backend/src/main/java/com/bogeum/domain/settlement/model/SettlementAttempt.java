package com.bogeum.domain.settlement.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.bogeum.domain.rent.model.PaymentAttempt;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 각 지급 요청의 은행 거래번호와 송금 대상을 변경 불가능한 스냅샷으로 보존한다. */
@Entity
@Table(name = "settlement_attempt", uniqueConstraints = {
	@UniqueConstraint(columnNames = {"settlement_id", "attempt_no"})
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementAttempt {
	public enum Status { REQUESTED, UNKNOWN, SUCCEEDED, FAILED }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long settlementAttemptId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "settlement_id", nullable = false, updatable = false)
	private Settlement settlement;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "payment_attempt_id", nullable = false, updatable = false)
	private PaymentAttempt collectionAttempt;

	@Column(nullable = false, updatable = false)
	private Integer attemptNo;

	@Column(nullable = false, length = 20, unique = true, updatable = false)
	private String bankTranId;

	// 회사 약정계좌 원문을 보관하지 않고 당시 계좌의 SHA-256 식별자를 남긴다.
	@Column(nullable = false, length = 64, updatable = false)
	private String institutionAccountReference;

	@Column(nullable = false, precision = 15, scale = 0, updatable = false)
	private BigDecimal amount;

	@Column(nullable = false, length = 24, updatable = false)
	private String tenantFintechUseNumSnapshot;

	@Column(nullable = false, length = 50, updatable = false)
	private String tenantNameSnapshot;

	@Column(nullable = false, updatable = false)
	private Long tenantUserIdSnapshot;

	@Column(nullable = false, length = 3, updatable = false)
	private String bankCodeSnapshot;

	@Column(nullable = false, updatable = false)
	private String accountNumberEncryptedSnapshot;

	@Column(nullable = false, length = 30, updatable = false)
	private String accountNumberMaskedSnapshot;

	@Column(nullable = false, length = 50, updatable = false)
	private String holderNameSnapshot;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Status attemptStatus = Status.REQUESTED;

	@Column(length = 50)
	private String responseCode;

	@Builder.Default
	@Column(nullable = false, updatable = false)
	private LocalDateTime requestedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

	private LocalDateTime completedAt;

	public boolean isUnresolved() {
		return attemptStatus == Status.REQUESTED || attemptStatus == Status.UNKNOWN;
	}

	public void succeed(String responseCode) {
		this.attemptStatus = Status.SUCCEEDED;
		this.responseCode = responseCode;
		this.completedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
	}

	public void markUnknown(String responseCode) {
		this.attemptStatus = Status.UNKNOWN;
		this.responseCode = responseCode;
	}

	public void failConfirmed() {
		this.attemptStatus = Status.FAILED;
		this.completedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
	}
}
