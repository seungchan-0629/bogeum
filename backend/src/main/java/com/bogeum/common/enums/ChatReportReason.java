package com.bogeum.common.enums;

/**
 * 채팅 신고 사유 (CHAT_001, CHAT_011)
 */
public enum ChatReportReason {
	FALSE_LISTING, // 허위 매물
	RISKY_PROPERTY, // 위험 매물
	RISK_INFO, // 위험 정보
	ABUSIVE, // 욕설·비매너
	FRAUD, // 사기 의심
	ETC // 기타 (내용 필수)
}
