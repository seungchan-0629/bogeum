package com.bogeum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 연결 대상 회원은 요청 본문이 아닌 현재 로그인 세션에서 결정한다. */
public record SocialLinkRequestDTO(
	@NotBlank(message = "비밀번호를 입력해 주세요.")
	@Size(max = 72, message = "비밀번호는 72바이트 이하여야 합니다.") String password) { }
