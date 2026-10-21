package com.bogeum.domain;

/**
 * 중개사 서류 심사 상태: 제출 필요 → 심사 대기 → 승인 또는 반려. 가입 시에는 DOCUMENT_REQUIRED로 시작한다.
 */
public enum AgentVerificationStatus {

	DOCUMENT_REQUIRED, PENDING_REVIEW, APPROVED, REJECTED
}
