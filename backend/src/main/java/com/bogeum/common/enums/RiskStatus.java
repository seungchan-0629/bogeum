package com.bogeum.common.enums;

/**
 * 위험 정보 상태. 월세 연체가 다음 달까지 이어지면 시스템이 후보로 만든다 (RENT_007)
 */
public enum RiskStatus {
	CANDIDATE, // 후보
	REGISTERED, // 등록
	RELEASED // 해제
}
