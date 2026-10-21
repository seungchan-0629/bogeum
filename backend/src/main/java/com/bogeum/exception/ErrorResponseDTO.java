package com.bogeum.exception;

import java.util.Map;

import lombok.Getter;

/**
 * 실패 응답의 공통 JSON 형식. code는 오류 식별자, message는 안내문, fieldErrors는 입력 항목별 사유이다.
 */
@Getter
public class ErrorResponseDTO {

	private final String code;
	private final String message;
	private final Map<String, String> fieldErrors;

	public ErrorResponseDTO(ErrorCode code) {
		this(code, Map.of());
	}

	public ErrorResponseDTO(ErrorCode code, Map<String, String> fieldErrors) {
		this.code = code.name();
		this.message = code.getMessage();
		this.fieldErrors = Map.copyOf(fieldErrors);
	}
}
