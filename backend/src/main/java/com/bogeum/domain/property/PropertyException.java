package com.bogeum.domain.property;

import com.bogeum.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class PropertyException extends RuntimeException {
	private final ErrorCode errorCode;
	private PropertyException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}
	public static PropertyException invalidLeasePrice() { return new PropertyException(ErrorCode.INVALID_LEASE_PRICE); }
	public static PropertyException agentNotAvailable() { return new PropertyException(ErrorCode.BROKERAGE_AGENT_NOT_AVAILABLE); }
	public static PropertyException noAgentAvailable() { return new PropertyException(ErrorCode.NO_BROKERAGE_AGENT); }
}
