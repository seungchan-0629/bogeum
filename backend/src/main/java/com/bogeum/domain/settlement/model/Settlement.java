package com.bogeum.domain.settlement.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.bogeum.common.enums.SettlementStatus;
import com.bogeum.domain.rent.model.RentPayment;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 월세 지급 (SETTLE_001, SETTLE_002)
 */
@Entity
@Table(name = "settlement")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Settlement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long settlementId;

	// 같은 결제 건의 지급 요청은 한 번만 만들어진다 (SETTLE_001)
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rent_payment_id", nullable = false, unique = true)
	private RentPayment rentPayment;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "landlord_id", nullable = false)
	private User landlord;

	// 정산 계좌가 없으면 비어 있고 지급은 보류된다 (NOTIFY_014)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "settlement_account_id")
	private SettlementAccount settlementAccount;

	// 결제 금액 전액이다 (SETTLE_001)
	@Column(nullable = false, precision = 15, scale = 0)
	private BigDecimal amount;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private SettlementStatus settlementStatus = SettlementStatus.PENDING;

	private String failureReason;

	@Column(length = 20, unique = true)
	private String bankTranId;

	private LocalDateTime paidAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public void startTransfer(SettlementAccount account, String bankTranId) {
		this.settlementAccount = account;
		this.bankTranId = bankTranId;
		this.settlementStatus = SettlementStatus.PROCESSING;
		this.failureReason = null;
	}

	public void complete() {
		this.settlementStatus = SettlementStatus.COMPLETED;
		this.paidAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
		this.failureReason = null;
	}

	public void markUnknown(String code) {
		this.settlementStatus = SettlementStatus.UNKNOWN;
		this.failureReason = "오픈뱅킹 원거래 결과 확인 필요: " + code;
	}

	/** 증빙으로 원거래의 실패를 확정한 경우에만 새 지급 시도를 허용한다. */
	public void failConfirmed() {
		this.settlementStatus = SettlementStatus.FAILED;
		this.failureReason = "증빙에 따라 원거래 지급 실패 확정";
	}
}
