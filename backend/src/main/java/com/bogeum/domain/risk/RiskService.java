package com.bogeum.domain.risk;

import java.util.Arrays;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.common.enums.RiskStatus;
import com.bogeum.common.enums.RiskTargetType;
import com.bogeum.domain.risk.model.RiskInfo;
import com.bogeum.domain.risk.model.RiskUserType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RiskService {
	private static final int PAGE_SIZE = 5;
	private final RiskRepository riskRepository;

	/** 등록된 사용자 위험정보만 조회한다. 사용자 계정 역할이나 계약으로 분류하지 않는다. */
	@Transactional(readOnly = true)
	public Page<RiskInfo> readRiskUsers(String type, String keyword, int page) {
		RiskUserType userType = null;
		if (type != null && !type.isEmpty()) {
			try {
				userType = RiskUserType.valueOf(type);
			} catch (IllegalArgumentException exception) {
				throw RiskException.invalidRequest();
			}
		}
		var reasons = userType == null
			? Arrays.stream(RiskUserType.values()).flatMap(value -> value.getReasons().stream()).toList()
			: userType.getReasons();
		var pageable = PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE);
		// #34와 같은 페이지 보정 및 JPA int 오프셋 범위 검증을 적용한다.
		if (pageable.getOffset() > Integer.MAX_VALUE) {
			throw RiskException.invalidRequest();
		}
		return riskRepository.findAdminRiskUsers(RiskTargetType.USER, RiskStatus.REGISTERED,
			reasons, keyword == null ? "" : keyword.strip(), pageable);
	}
}
