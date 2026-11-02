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

	private LocalDateTime paidAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private LocalDateTime updatedAt;
}
