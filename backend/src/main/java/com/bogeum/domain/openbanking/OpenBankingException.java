package com.bogeum.domain.openbanking;

import com.bogeum.common.exception.ErrorCode;
import lombok.Getter;

/** 은행 원문 응답이나 인증 토큰을 클라이언트 오류에 포함하지 않는다. */
@Getter
public class OpenBankingException extends RuntimeException {
	private final ErrorCode errorCode;

	private OpenBankingException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public static OpenBankingException unavailable() { return of(ErrorCode.OPEN_BANKING_UNAVAILABLE); }
	public static OpenBankingException invalidResponse() { return unavailable(); }
	public static OpenBankingException of(ErrorCode code) { return new OpenBankingException(code); }
}
