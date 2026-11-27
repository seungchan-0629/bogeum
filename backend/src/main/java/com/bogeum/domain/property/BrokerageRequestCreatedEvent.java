package com.bogeum.domain.property;

/** 임대인이 중개를 신청했다. 저장이 확정되면 배정된 중개사에게 알림이 간다 (NOTIFY_022) */
public record BrokerageRequestCreatedEvent(Long brokerageRequestId, Long agentUserId, String landlordName,
		String roadAddress) {
}
