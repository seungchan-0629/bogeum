package com.bogeum.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** API 오류의 HTTP 상태와 사용자에게 노출할 메시지를 정의한다. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청값을 확인해 주세요."),
	INVALID_SIGNUP(HttpStatus.BAD_REQUEST, "가입 정보와 필수 동의 항목을 확인해 주세요."),
	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
	SIGNUP_CONFLICT(HttpStatus.CONFLICT, "가입 정보를 저장할 수 없습니다. 입력 정보를 확인해 주세요."),
	LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해 주세요.");

	private final HttpStatus httpStatus;
	private final String message;
}
