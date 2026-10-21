package com.bogeum.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bogeum.domain.MemberType;
import com.bogeum.dto.TermsResponseDTO;
import com.bogeum.service.ConsentService;

import lombok.RequiredArgsConstructor;

/**
 * 가입 화면에서 회원 유형에 맞는 약관과 현재 가입 가능 여부를 조회하는 진입점.
 */
@RestController
@RequiredArgsConstructor
public class TermsController {

	private final ConsentService consentService;

	@GetMapping("/api/terms")
	public TermsResponseDTO terms(@RequestParam(defaultValue = "GENERAL") MemberType memberType) {
		return consentService.getTerms(memberType);
	}
}
