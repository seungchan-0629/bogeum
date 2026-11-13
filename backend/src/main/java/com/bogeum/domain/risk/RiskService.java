package com.bogeum.domain.risk;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.common.enums.RiskReason;
import com.bogeum.common.enums.RiskStatus;
import com.bogeum.common.enums.RiskTargetType;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.risk.model.RiskInfo;
import com.bogeum.domain.risk.model.RiskUserType;
import com.bogeum.domain.user.UserException;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RiskService {
	private static final int PAGE_SIZE = 5;
	// MySQL TEXT 컬럼의 최대 저장 크기이며 글자 수가 아닌 UTF-8 바이트로 검사한다.
	private static final int MAX_DETAIL_BYTES = 65_535;
	private final RiskRepository riskRepository;
	private final UserRepository userRepository;

	/** 신규 등록에만 역할 제한을 적용한다. 기존 기록의 조회·후보·해제 상태는 변경하지 않는다. */
	@Transactional
	public RiskInfo createRiskUser(Long adminUserId, Long userId, RiskReason reasonType, String detail) {
		User administrator = userRepository.findById(adminUserId).orElseThrow(UserException::required);
		if (administrator.getRole() != Role.ADMIN) {
			throw RiskException.accessDenied();
		}
		User target = userRepository.findById(userId).orElseThrow(RiskException::userNotFound);
		RiskUserType userType;
		try {
			userType = RiskUserType.fromReason(reasonType);
		} catch (IllegalArgumentException exception) {
			throw RiskException.invalidRequest();
		}
		// 임대인·임차인 사유는 계약 이력 없이 등록하며 중개사도 해당 사유의 대상이 될 수 있다.
		boolean allowed = switch (target.getRole()) {
			case USER -> userType == RiskUserType.LANDLORD || userType == RiskUserType.TENANT;
			case AGENT -> true;
			case LANDLORD -> userType == RiskUserType.LANDLORD;
			case TENANT -> userType == RiskUserType.TENANT;
			case ADMIN -> false;
		};
		if (!allowed || (detail != null && detail.getBytes(StandardCharsets.UTF_8).length > MAX_DETAIL_BYTES)) {
			throw RiskException.invalidRequest();
		}
		// 조회 후 저장이므로 동시 요청의 중복까지 보장하지 않는다. 기존 기록은 삭제하거나 합치지 않는다.
		if (riskRepository.existsByTargetTypeAndTargetUser_UserIdAndReasonTypeAndRiskStatus(
				RiskTargetType.USER, userId, reasonType, RiskStatus.REGISTERED)) {
			throw RiskException.alreadyRegistered();
		}
		return riskRepository.save(RiskInfo.builder().targetType(RiskTargetType.USER).targetUser(target)
			.reasonType(reasonType).detail(detail).riskStatus(RiskStatus.REGISTERED)
			.registeredBy(administrator).build());
	}

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
