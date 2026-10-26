package com.bogeum.common.exception;

import lombok.Getter;

/** 클라이언트에 반환할 공통 오류 형식. 입력 비밀번호나 예외 내부 정보는 포함하지 않는다. */
@Getter
public class ErrorResponse {

	private final String code;
	private final String message;

	public ErrorResponse(ErrorCode errorCode) {
		this.code = errorCode.name();
		this.message = errorCode.getMessage();
	}
}
