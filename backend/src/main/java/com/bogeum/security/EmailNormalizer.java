package com.bogeum.security;

import java.util.Locale;

/**
 * 가입·로그인·본인 인증에서 이메일을 동일한 기준으로 비교하도록 앞뒤 공백과 대소문자를 정규화한다.
 */
public final class EmailNormalizer {

	private EmailNormalizer() {
	}

	public static String normalize(String email) {
		return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
	}
}
