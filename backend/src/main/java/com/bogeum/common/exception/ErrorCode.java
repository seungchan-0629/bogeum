package com.bogeum.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** API 오류의 HTTP 상태와 사용자에게 노출할 메시지를 정의한다. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청값을 확인해 주세요."),
	LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해 주세요.");

	private final HttpStatus httpStatus;
	private final String message;
}
