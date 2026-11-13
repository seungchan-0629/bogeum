package com.bogeum.domain.risk.model;

import java.util.Arrays;
import java.util.List;

import com.bogeum.common.enums.RiskReason;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 계정 역할이 아닌 위험 사유로 결정하는 조회용 분류. DB에는 저장하지 않는다. */
@Getter
@RequiredArgsConstructor
public enum RiskUserType {
	TENANT(List.of(RiskReason.RENT_UNPAID, RiskReason.RENT_OVERDUE)),
	LANDLORD(List.of(RiskReason.DEPOSIT_UNPAID, RiskReason.FALSE_LISTING)),
	AGENT(List.of(RiskReason.NO_PROPERTY_IMAGE, RiskReason.AGENT_CHANGE_HISTORY));

	private final List<RiskReason> reasons;

	/** 필터와 응답이 동일한 사유 분류를 사용한다. 매물 위험 사유는 포함하지 않는다. */
	public static RiskUserType fromReason(RiskReason reason) {
		return Arrays.stream(values()).filter(type -> type.reasons.contains(reason))
			.findFirst().orElseThrow(() -> new IllegalArgumentException("사용자 위험 사유가 아닙니다."));
	}
}
