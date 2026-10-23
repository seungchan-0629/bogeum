package com.bogeum.domain.rent.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.bogeum.common.enums.PaymentAttemptStatus;

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

/**
 * 결제 시도 (RENT_006)
 */
@Entity
@Table(name = "payment_attempt", uniqueConstraints = {
	@UniqueConstraint(columnNames = {"rent_payment_id", "attempt_no"})
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long paymentAttemptId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rent_payment_id", nullable = false)
	private RentPayment rentPayment;

	// 1: 납부일 최초 결제, 2: 즉시 재시도, 3: 최초 실패일 기준 3일 후, 4: 7일 후
	@Column(nullable = false)
	private Integer attemptNo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "payment_method_id", nullable = false)
	private PaymentMethod paymentMethod;

	@Column(nullable = false, precision = 15, scale = 0)
	private BigDecimal amount;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private PaymentAttemptStatus attemptStatus = PaymentAttemptStatus.REQUESTED;

	// PG 또는 CMS 업체가 돌려준 거래 번호
	@Column(length = 64)
	private String pgTransactionId;

	@Column(length = 50)
	private String failureCode;

	private String failureMessage;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime requestedAt;

	// 성공 또는 실패가 확정된 시각
	private LocalDateTime completedAt;
}
