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

	public static RiskException accessDenied() {
		return new RiskException(ErrorCode.ACCESS_DENIED);
	}

	public static RiskException userNotFound() {
		return new RiskException(ErrorCode.RISK_USER_NOT_FOUND);
	}

	public static RiskException alreadyRegistered() {
		return new RiskException(ErrorCode.RISK_ALREADY_REGISTERED);
	}
}
