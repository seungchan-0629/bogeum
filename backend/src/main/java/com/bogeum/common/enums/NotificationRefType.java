package com.bogeum.common.enums;

/**
 * 알림이 가리키는 대상. 알림을 선택하면 관련 화면으로 이동한다 (NOTIFY_001, NOTIFY_007, NOTIFY_021)
 */
public enum NotificationRefType {
	CONTRACT, // 계약
	RENT_PAYMENT, // 월세 납부
	SETTLEMENT, // 월세 지급
	PROPERTY, // 매물
	BROKERAGE_REQUEST, // 중개 신청
	AGENT_PROFILE, // 중개사 정보
	AS_REQUEST, // AS 요청
	CLAIM, // 클레임
	CHAT_ROOM, // 채팅방
	RISK_INFO // 위험 정보
}
