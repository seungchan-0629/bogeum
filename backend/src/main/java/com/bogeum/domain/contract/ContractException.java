package com.bogeum.domain.contract;

import com.bogeum.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class ContractException extends RuntimeException {
	private final ErrorCode errorCode;
	private ContractException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}
	public static ContractException invalidTenant() { return new ContractException(ErrorCode.INVALID_CONTRACT_TENANT); }
	public static ContractException invalidPrice() { return new ContractException(ErrorCode.INVALID_CONTRACT_PRICE); }
	public static ContractException invalidPeriod() { return new ContractException(ErrorCode.INVALID_CONTRACT_PERIOD); }
	public static ContractException invalidContractedAt() { return new ContractException(ErrorCode.INVALID_CONTRACTED_AT); }
	public static ContractException invalidImage() { return new ContractException(ErrorCode.INVALID_IMAGE); }
	public static ContractException storageUnavailable() { return new ContractException(ErrorCode.CONTRACT_STORAGE_UNAVAILABLE); }
}
