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

	// 카드일 때 PG사가 발급한 빌링키. 카드번호는 직접 저장하지 않는다 (USER_013)
	private String billingKey;

	// 이하 계좌일 때만 채운다. 계좌번호 평문은 저장하지 않는다 (USER_014)
	@Column(length = 100)
	private String cmsRegistrationId;

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
}
