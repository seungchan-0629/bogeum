package com.bogeum.common.enums;

/**
 * 월세 지급 상태 (SETTLE_001, SETTLE_002)
 */
public enum SettlementStatus {
	PENDING, // 보류·대기
	COMPLETED, // 지급 완료
	FAILED // 지급 실패
}
