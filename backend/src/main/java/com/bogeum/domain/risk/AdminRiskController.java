package com.bogeum.domain.risk;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bogeum.domain.risk.model.RiskDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AdminRiskController {

	private final RiskService riskService;

	/** 관리자용 위험 사용자 목록. 증빙 파일 열람은 별도 기능이다 (#44). */
	@GetMapping("/admin/risk-users")
	public RiskDto.UserListRes readRiskUsers(@RequestParam(defaultValue = "") String type,
			@RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "1") int page) {
		return RiskDto.UserListRes.from(riskService.readRiskUsers(type, keyword, page));
	}
}
