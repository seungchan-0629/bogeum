package com.bogeum.domain.risk;

import com.bogeum.common.exception.ErrorCode;

import lombok.Getter;

@Getter
public class RiskException extends RuntimeException {
	private final ErrorCode errorCode;

	private RiskException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public static RiskException invalidRequest() {
		return new RiskException(ErrorCode.INVALID_REQUEST);
	}
}
