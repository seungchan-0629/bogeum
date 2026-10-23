package com.bogeum.common.enums;

/**
 * 매물 상태
 */
public enum PropertyStatus {
	REQUESTED, // 중개 신청됨
	ACTIVE, // 공개
	HIDDEN, // 숨김. 관리자가 위험 매물을 임차인에게 안 보이게 한 상태 (RISK_005)
	CONTRACTED, // 계약됨
	DELETED // 삭제. 행과 채팅 내역은 보존한다 (CHAT_009)
}
