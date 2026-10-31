package com.bogeum.domain.rent.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
	@JoinColumn(name = "rent_payment_id", nullable = false, updatable = false)
	private RentPayment rentPayment;

	// 1: 납부일 최초 결제, 2: 즉시 재시도, 3: 최초 실패일 기준 3일 후, 4: 7일 후
	@Column(nullable = false, updatable = false)
	private Integer attemptNo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "payment_method_id", nullable = false, updatable = false)
	private PaymentMethod paymentMethod;

	@Column(nullable = false, precision = 15, scale = 0, updatable = false)
	private BigDecimal amount;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private PaymentAttemptStatus attemptStatus = PaymentAttemptStatus.REQUESTED;

	// 외부 요청 전에 저장한다. 응답이 유실되어도 같은 거래를 추적할 수 있다.
	@Column(length = 20, unique = true, updatable = false)
	private String bankTranId;

	// 송금 이후 계좌 연결·회원 정보가 바뀌어도 원거래 식별을 보존한다. 과거 행은 null이다.
	@Column(length = 24, updatable = false)
	private String fintechUseNumSnapshot;
	@Column(length = 50, updatable = false)
	private String tenantNameSnapshot;
	@Column(updatable = false)
	private Long tenantUserIdSnapshot;
	@Column(length = 64, updatable = false)
	private String institutionAccountReference;

	@Column(length = 50)
	private String failureCode;

	private String failureMessage;

	@Builder.Default
	@Column(nullable = false, updatable = false)
	private LocalDateTime requestedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

	// 성공 또는 실패가 확정된 시각
	private LocalDateTime completedAt;

	public void succeed() {
		this.attemptStatus = PaymentAttemptStatus.SUCCEEDED;
		this.completedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
		this.failureCode = null;
		this.failureMessage = null;
	}

	/** 은행 증빙으로 원거래 실패가 확정된 경우에만 다시 출금할 수 있다. */
	public void confirmFailure() {
		this.attemptStatus = PaymentAttemptStatus.FAILED;
		this.completedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
		this.failureCode = "MANUALLY_CONFIRMED";
		this.failureMessage = "관리자가 은행 원거래 증빙으로 실패를 확인했습니다.";
	}

	public void markUnknown(String code) {
		this.attemptStatus = PaymentAttemptStatus.UNKNOWN;
		this.failureCode = code;
		this.failureMessage = "오픈뱅킹 원거래 결과를 확인해야 합니다. 재출금하지 마세요.";
	}
}
