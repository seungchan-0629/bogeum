package com.bogeum.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bogeum.dto.AgentJoinDTO;
import com.bogeum.dto.MemberResponseDTO;
import com.bogeum.service.AgentRegistrationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 중개사 회원가입의 HTTP 진입점. 요청 DTO를 검증한 후 가입 서비스에 처리를 맡긴다.
 */
@RestController
@RequestMapping("/api/auth/signup")
@RequiredArgsConstructor
public class AgentController {

	private final AgentRegistrationService registrationService;

	@PostMapping("/agent")
	@ResponseStatus(HttpStatus.CREATED)
	public MemberResponseDTO register(@Valid @RequestBody AgentJoinDTO request) {
		// 중개사 계정 생성과 서류 심사는 별개다. 가입 응답에는 서류 제출 필요 상태와 제출 기한이 포함된다.
		return registrationService.register(request);
	}
}
