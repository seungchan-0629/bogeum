package com.bogeum.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bogeum.dto.MemberJoinDTO;
import com.bogeum.dto.MemberResponseDTO;
import com.bogeum.service.MemberRegistrationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 일반 회원가입의 HTTP 진입점. 요청 DTO를 검증한 후 가입 서비스에 처리를 맡긴다.
 */
@RestController
@RequestMapping("/api/auth/signup")
@RequiredArgsConstructor
public class MemberController {

	private final MemberRegistrationService registrationService;

	@PostMapping("/member")
	@ResponseStatus(HttpStatus.CREATED)
	public MemberResponseDTO register(@Valid @RequestBody MemberJoinDTO request) {
		// @Valid 검증을 통과한 요청만 서비스에 도달하며, 가입 완료 후 201을 반환한다. 자동 로그인은 하지 않는다.
		return registrationService.register(request);
	}
}
