package com.bogeum.common.enums;

/**
 * 월세 납부 상태 (RENT_005)
 */
public enum RentPaymentStatus {
	SCHEDULED, // 예정
	PAID, // 성공
	FAILED, // 실패 (재시도 중)
	OVERDUE // 연체
}
