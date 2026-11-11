package com.bogeum.domain.openbanking;

import com.bogeum.common.exception.ErrorCode;
import lombok.Getter;

/*
안쪽에 OpenbankingException을 상속하는 형태로 에러들을 구현
특히 200으로 반환되지만 에러인것들도 잡아내서 이쪽으로 throw
*/
@Getter
public class OpenBankingException extends RuntimeException {
	private final ErrorCode errorCode;

    public OpenBankingException(String message) {
        super(message);
		this.errorCode = ErrorCode.OPENBANKING_FAILED;
    }

	private OpenBankingException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public static OpenBankingException invalidRequest() { return new OpenBankingException(ErrorCode.INVALID_REQUEST); }
	public static OpenBankingException accessDenied() { return new OpenBankingException(ErrorCode.ACCESS_DENIED); }
	public static OpenBankingException contractNotFound() { return new OpenBankingException(ErrorCode.CONTRACT_NOT_FOUND); }
	public static OpenBankingException accountNotFound() { return new OpenBankingException(ErrorCode.OPENBANKING_ACCOUNT_NOT_FOUND); }
}
