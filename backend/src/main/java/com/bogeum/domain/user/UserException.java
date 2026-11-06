package com.bogeum.domain.user;

import com.bogeum.common.exception.ErrorCode;

import lombok.Getter;

/** 사용자 가입·인증 실패를 전역 예외 처리기에 전달한다. */
@Getter
public class UserException extends RuntimeException {

	private final ErrorCode errorCode;

	private UserException(ErrorCode errorCode) {
		this(errorCode, null);
	}

	private UserException(ErrorCode errorCode, Throwable cause) {
		super(errorCode.getMessage(), cause);
		this.errorCode = errorCode;
	}

	/** 허용하지 않는 가입 역할·동의·비밀번호를 거절한다. */
	public static UserException invalidSignup() {
		return new UserException(ErrorCode.INVALID_SIGNUP);
	}

	public static UserException invalidPhone() { return new UserException(ErrorCode.INVALID_PHONE); }
	public static UserException smsNotConfigured() { return new UserException(ErrorCode.SMS_NOT_CONFIGURED); }
	public static UserException smsSendFailed(Throwable cause) { return new UserException(ErrorCode.SMS_SEND_FAILED, cause); }
	public static UserException smsRateLimited() { return new UserException(ErrorCode.SMS_RATE_LIMITED); }
	public static UserException phoneVerificationInvalid() { return new UserException(ErrorCode.PHONE_VERIFICATION_INVALID); }
	public static UserException phoneVerificationExpired() { return new UserException(ErrorCode.PHONE_VERIFICATION_EXPIRED); }
	public static UserException phoneCodeMismatch() { return new UserException(ErrorCode.PHONE_CODE_MISMATCH); }
	public static UserException phoneAttemptsExceeded() { return new UserException(ErrorCode.PHONE_ATTEMPTS_EXCEEDED); }
	public static UserException phoneVerificationRequired() { return new UserException(ErrorCode.PHONE_VERIFICATION_REQUIRED); }

	/** 기존 계정의 이메일을 재사용할 수 없다. */
	public static UserException emailAlreadyExists() {
		return new UserException(ErrorCode.EMAIL_ALREADY_EXISTS);
	}

	/** 저장 제약 위반의 원인을 보존하고 가입 트랜잭션을 롤백한다. */
	public static UserException signupConflict(Throwable cause) {
		return new UserException(ErrorCode.SIGNUP_CONFLICT, cause);
	}

	/** 계정 없음·비밀번호 불일치·탈퇴·소셜 전용 계정을 같은 오류로 처리한다. */
	public static UserException loginFailed() {
		return new UserException(ErrorCode.LOGIN_FAILED);
	}

	public static UserException invalidProfile() { return new UserException(ErrorCode.INVALID_PROFILE); }
	public static UserException currentPasswordMismatch() { return new UserException(ErrorCode.CURRENT_PASSWORD_MISMATCH); }
	public static UserException naverNotConfigured() { return new UserException(ErrorCode.NAVER_NOT_CONFIGURED); }
	public static UserException naverAuthFailed() { return new UserException(ErrorCode.NAVER_AUTH_FAILED); }
	public static UserException kakaoNotConfigured() { return new UserException(ErrorCode.KAKAO_NOT_CONFIGURED); }
	public static UserException kakaoAuthFailed() { return new UserException(ErrorCode.KAKAO_AUTH_FAILED); }
	public static UserException socialAuthExpired() { return new UserException(ErrorCode.SOCIAL_AUTH_EXPIRED); }
	public static UserException socialEmailConflict() { return new UserException(ErrorCode.SOCIAL_EMAIL_CONFLICT); }
	public static UserException socialAlreadyConnected() { return new UserException(ErrorCode.SOCIAL_ALREADY_CONNECTED); }
	public static UserException socialRoleNotAllowed() { return new UserException(ErrorCode.SOCIAL_ROLE_NOT_ALLOWED); }

	public static UserException required() { return new UserException(ErrorCode.AUTH_REQUIRED); }
	public static UserException expired() { return new UserException(ErrorCode.ACCESS_TOKEN_EXPIRED); }
	public static UserException invalidAccess() { return new UserException(ErrorCode.ACCESS_TOKEN_INVALID); }
	public static UserException invalidRefresh() { return new UserException(ErrorCode.REFRESH_TOKEN_INVALID); }
}
