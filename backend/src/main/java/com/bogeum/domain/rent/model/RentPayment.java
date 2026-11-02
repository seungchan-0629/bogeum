package com.bogeum.domain.rent.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.bogeum.common.enums.RentPaymentStatus;
import com.bogeum.domain.contract.model.Contract;

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
 * 월세 납부 회차 (RENT_004, RENT_005)
 */
@Entity
@Table(name = "rent_payment", uniqueConstraints = {
	@UniqueConstraint(columnNames = {"contract_id", "seq"})
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentPayment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long rentPaymentId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "contract_id", nullable = false)
	private Contract contract;

	// 같은 회차는 한 번만 만들어져 같은 달 월세가 중복 결제되지 않는다 (RENT_004)
	@Column(nullable = false)
	private Integer seq;

	// 이 날 자동 결제한다
	@Column(nullable = false)
	private LocalDateTime dueAt;

	// 계약서의 월세 금액과 같다 (RENT_004)
	@Column(nullable = false, precision = 15, scale = 0)
	private BigDecimal amount;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private RentPaymentStatus rentPaymentStatus = RentPaymentStatus.SCHEDULED;

	@Builder.Default
	@Column(nullable = false)
	private Integer retryCount = 0;

	// 3일 후, 7일 후 재시도의 기준 (RENT_006)
	private LocalDateTime firstFailedAt;

	private LocalDateTime nextRetryAt;

	private LocalDateTime paidAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public void startCollection() {
		this.rentPaymentStatus = RentPaymentStatus.PROCESSING;
	}

	public void markPaid() {
		this.rentPaymentStatus = RentPaymentStatus.PAID;
		this.paidAt = LocalDateTime.now();
		this.nextRetryAt = null;
	}

	public void markUnknown() {
		this.rentPaymentStatus = RentPaymentStatus.UNKNOWN;
		this.nextRetryAt = null;
	}
}
