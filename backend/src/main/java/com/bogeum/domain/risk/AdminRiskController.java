package com.bogeum.domain.risk;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bogeum.domain.risk.model.RiskDto;
import com.bogeum.domain.user.model.UserDto.LoginUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AdminRiskController {

	private final RiskService riskService;

	/** 인증된 관리자가 사용자 위험정보를 신규 등록한다 (#42). */
	@PostMapping("/admin/risk-users")
	@ResponseStatus(HttpStatus.CREATED)
	public RiskDto.UserItemRes createRiskUser(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody RiskDto.UserRegReq request) {
		return RiskDto.UserItemRes.from(riskService.createRiskUser(loginUser.getUserId(),
			request.getUserId(), request.getReasonType(), request.getDetail()));
	}

	/** 관리자용 위험 사용자 목록. 증빙 파일 열람은 별도 기능이다 (#44). */
	@GetMapping("/admin/risk-users")
	public RiskDto.UserListRes readRiskUsers(@RequestParam(defaultValue = "") String type,
			@RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "1") int page) {
		return RiskDto.UserListRes.from(riskService.readRiskUsers(type, keyword, page));
	}
}
