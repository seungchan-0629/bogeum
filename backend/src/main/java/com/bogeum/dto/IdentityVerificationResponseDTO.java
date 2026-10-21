package com.bogeum.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 개발용 인증 토큰과 만료 시각을 반환한다. 클라이언트는 token을 일반 회원가입 요청에 포함한다.
 */
@Getter
@AllArgsConstructor
public class IdentityVerificationResponseDTO {

	private final String token;
	private final Instant expiresAt;
	private final boolean developmentOnly = true;
}
