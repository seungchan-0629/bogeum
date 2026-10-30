package com.bogeum.domain.rent.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.bogeum.common.enums.PaymentMethodStatus;
import com.bogeum.common.enums.PaymentMethodType;
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
 * 임차인 결제 수단 (USER_013, USER_014)
 */
@Entity
@Table(name = "payment_method")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethod {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long paymentMethodId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tenant_id", nullable = false)
	private User tenant;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private PaymentMethodType methodType;

	// 오픈뱅킹에서 등록·출금 동의를 확인한 계좌의 식별자다.
	@Column(length = 24)
	private String fintechUseNum;

	@Column(length = 10)
	private String bankCode;

	@Column(length = 30)
	private String accountNumberMasked;

	// 비어 있으면 출금 이체 약관 미동의
	private LocalDateTime withdrawalConsentedAt;

	// 다시 등록하면 기존 수단은 REPLACED로 바꾼다. 임차인당 사용 중인 수단은 1개다
	@Builder.Default
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private PaymentMethodStatus paymentMethodStatus = PaymentMethodStatus.ACTIVE;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public void replace() {
		this.paymentMethodStatus = PaymentMethodStatus.REPLACED;
	}
}
