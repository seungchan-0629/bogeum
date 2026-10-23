package com.bogeum.common.enums;

/**
 * 위험 정보 사유 (RISK_001, RISK_006)
 */
public enum RiskReason {
	DEPOSIT_UNPAID, // 보증금 미지급 (임대인)
	FALSE_LISTING, // 허위 매물 등록 (임대인)
	RENT_UNPAID, // 월세 미지급 (임차인)
	RENT_OVERDUE, // 연체 (임차인)
	NO_PROPERTY_IMAGE, // 매물 이미지 미등록 (중개사)
	AGENT_CHANGE_HISTORY, // 중개사 교체 이력 (중개사)
	MORTGAGE_RISK // 근저당 등 위험 매물
}
