package com.bogeum.common.enums;

/**
 * 결제 시도 상태
 */
public enum PaymentAttemptStatus {
	REQUESTED, // 요청됨
	UNKNOWN, // 은행 처리 결과 확인 필요. 재출금 금지
	SUCCEEDED, // 성공
	FAILED // 실패
}
