package com.bogeum.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bogeum.dto.IdentityVerificationRequestDTO;
import com.bogeum.dto.IdentityVerificationResponseDTO;
import com.bogeum.service.DevIdentityVerificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * dev/test 환경에서만 제공하는 모의 본인 인증 API. 외부 기관의 실제 인증을 수행하지 않는다.
 */
@RestController
@Profile({"dev", "test"})
@RequiredArgsConstructor
public class DevIdentityVerificationController {

	private final DevIdentityVerificationService verificationService;

	@PostMapping("/api/dev/identity-verifications")
	@ResponseStatus(HttpStatus.CREATED)
	public IdentityVerificationResponseDTO issue(@Valid @RequestBody IdentityVerificationRequestDTO request) {
		return verificationService.issue(request);
	}
}
