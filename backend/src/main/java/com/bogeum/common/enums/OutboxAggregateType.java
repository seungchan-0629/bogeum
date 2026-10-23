package com.bogeum.common.enums;

/**
 * 이벤트가 발생한 대상 (NOTIFY_006)
 */
public enum OutboxAggregateType {
	RENT_PAYMENT, // 월세 납부
	SETTLEMENT, // 월세 지급
	PROPERTY, // 매물
	RISK_INFO, // 위험 정보
	CLAIM // 클레임
}
