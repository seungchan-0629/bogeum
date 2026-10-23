package com.bogeum.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 컨트롤러와 보안 필터가 공유하는 API 오류 코드·HTTP 상태·사용자 안내 메시지 목록.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	VALIDATION_FAILED(400, "입력값을 확인해 주세요."),
	INVALID_JSON(400, "올바른 JSON 요청을 보내 주세요."),
	EMAIL_ALREADY_EXISTS(409, "이미 가입된 이메일입니다."),
	SOCIAL_LOGIN_FAILED(401, "소셜 로그인에 실패했습니다. 다시 시작해 주세요."),
	SOCIAL_LOGIN_UNAVAILABLE(503, "해당 소셜 로그인 설정이 준비되지 않았습니다."),
	SOCIAL_REGISTRATION_EXPIRED(401, "소셜 인증이 만료되었습니다. 소셜 로그인을 다시 시작해 주세요."),
	SOCIAL_ACCOUNT_ALREADY_LINKED(409, "이미 연결된 소셜 계정입니다."),
	SOCIAL_LINK_EXPIRED(401, "계정 연결 인증이 만료되었습니다. 연결을 다시 시작해 주세요."),
	DATA_CONFLICT(409, "이미 처리되었거나 중복된 요청입니다."),
	CONSENT_REQUIRED(400, "모든 필수 항목에 각각 동의해 주세요."),
	CONSENT_VERSION_MISMATCH(409, "최신 약관을 조회한 후 다시 동의해 주세요."),
	INVALID_IDENTITY_VERIFICATION(400, "유효한 본인 인증 증빙이 필요합니다."),
	REGISTRATION_UNAVAILABLE(503, "회원가입 준비 중입니다."),
	IDENTITY_VERIFICATION_UNAVAILABLE(503, "본인 인증 연동 준비 중입니다."),
	UNSUPPORTED_MEDIA_TYPE(415, "Content-Type은 application/json이어야 합니다."),
	INVALID_CREDENTIALS(401, "이메일 또는 비밀번호가 올바르지 않습니다."),
	UNAUTHENTICATED(401, "로그인이 필요합니다."),
	ACCESS_DENIED(403, "접근 권한이 없습니다."),
	CSRF_INVALID(403, "CSRF 토큰을 다시 조회한 후 요청해 주세요.");

	private final int status;
	private final String message;
}
