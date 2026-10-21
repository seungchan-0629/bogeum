package com.bogeum.exception;

import java.util.Map;

import lombok.Getter;

/**
 * 서비스의 업무 규칙 위반을 오류 코드와 필드별 사유로 전달한다. GlobalExceptionHandler가 HTTP 응답으로 변환한다.
 */
@Getter
public class ApiException extends RuntimeException {

	private final ErrorCode errorCode;
	private final Map<String, String> fieldErrors;

	public ApiException(ErrorCode errorCode) {
		this(errorCode, Map.of());
	}

	public ApiException(ErrorCode errorCode, Map<String, String> fieldErrors) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.fieldErrors = Map.copyOf(fieldErrors);
	}
}
