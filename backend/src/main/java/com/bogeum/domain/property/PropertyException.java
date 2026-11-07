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
	public static PropertyException brokerageRequestNotFound() { return new PropertyException(ErrorCode.BROKERAGE_REQUEST_NOT_FOUND); }
	public static PropertyException alreadyAccepted() { return new PropertyException(ErrorCode.BROKERAGE_ALREADY_ACCEPTED); }
	public static PropertyException propertyNotFound() { return new PropertyException(ErrorCode.PROPERTY_NOT_FOUND); }
	public static PropertyException notEditable() { return new PropertyException(ErrorCode.PROPERTY_NOT_EDITABLE); }
	public static PropertyException invalidFloor() { return new PropertyException(ErrorCode.INVALID_PROPERTY_FLOOR); }
	public static PropertyException invalidImage() { return new PropertyException(ErrorCode.INVALID_IMAGE); }
	public static PropertyException storageUnavailable() { return new PropertyException(ErrorCode.PROPERTY_STORAGE_UNAVAILABLE); }
}
