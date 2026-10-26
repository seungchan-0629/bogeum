package com.bogeum.domain.user;

import com.bogeum.common.exception.ErrorCode;

import lombok.Getter;

/** 사용자 인증 실패를 전역 예외 처리기에 전달한다. */
@Getter
public class UserException extends RuntimeException {

	private final ErrorCode errorCode;

	private UserException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	/** 계정 없음·비밀번호 불일치·탈퇴·소셜 전용 계정을 같은 오류로 처리한다. */
	public static UserException loginFailed() {
		return new UserException(ErrorCode.LOGIN_FAILED);
	}
}
