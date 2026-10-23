package com.bogeum.common.enums;

/**
 * 결제 수단 상태. 임차인당 사용 중인 수단은 1개다
 */
public enum PaymentMethodStatus {
	ACTIVE, // 사용 중
	REPLACED // 교체됨
}
