package com.bogeum.common.enums;

/**
 * 이벤트 발행 상태
 */
public enum OutboxEventStatus {
	PENDING, // 발행 대기
	PUBLISHED, // 발행 완료
	FAILED // 발행 실패
}
