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
}
