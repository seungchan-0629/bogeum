package com.bogeum.domain.rent;

import com.bogeum.common.exception.ErrorCode;

import lombok.Getter;

@Getter
public class RentException extends RuntimeException {
	private final ErrorCode errorCode;

	private RentException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public static RentException invalidRequest() {
		return new RentException(ErrorCode.INVALID_REQUEST);
	}
}
