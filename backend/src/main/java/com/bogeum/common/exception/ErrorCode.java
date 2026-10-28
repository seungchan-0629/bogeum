package com.bogeum.common.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청값을 확인해 주세요."),
	INVALID_SIGNUP(HttpStatus.BAD_REQUEST, "가입 정보와 필수 동의를 확인해 주세요."),
	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
	SIGNUP_CONFLICT(HttpStatus.CONFLICT, "가입 정보를 저장할 수 없습니다."),
	LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해 주세요."),
	AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "인증 시간이 만료되었습니다."),
	ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "올바르지 않은 인증 정보입니다."),
	REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	CSRF_INVALID(HttpStatus.FORBIDDEN, "요청 확인 정보가 만료되었습니다. 새로고침해 주세요.");
	private final HttpStatus httpStatus;
	private final String message;
}
