package com.bogeum.security;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.bogeum.exception.ApiException;
import com.bogeum.exception.ErrorCode;

/**
 * 가입 비밀번호 확인과 BCrypt가 처리할 수 있는 UTF-8 바이트 길이 제한을 공통으로 적용한다.
 */
public final class PasswordPolicy {

	// 한글처럼 여러 바이트인 문자가 있으므로 문자열 길이와 UTF-8 바이트 수를 구분한다.
	public static final int MAX_BYTES = 72;

	private PasswordPolicy() {
	}

	public static boolean exceedsByteLimit(String password) {
		return password != null && password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
	}

	public static void validateForRegistration(String password, String confirmation) {
		if (password == null || password.isBlank() || password.length() < 8 || exceedsByteLimit(password)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED,
				Map.of("account.password", "비밀번호는 8자 이상, 72바이트 이하여야 합니다."));
		}
		if (!password.equals(confirmation)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED,
				Map.of("account.passwordConfirm", "비밀번호와 비밀번호 확인이 일치하지 않습니다."));
		}
	}
}
