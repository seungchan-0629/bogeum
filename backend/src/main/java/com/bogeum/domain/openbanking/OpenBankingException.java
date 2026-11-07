package com.bogeum.domain.openbanking;

import com.bogeum.common.exception.ErrorCode;
import lombok.Getter;


/*
안쪽에 OpenbankingException을 상속하는 형태로 에러들을 구현
특히 200으로 반환되지만 에러인것들도 잡아내서 이쪽으로 throw
*/
@Getter
public class OpenBankingException extends RuntimeException {
    public OpenBankingException(String message) {
        super(message);
        this.errorCode = ErrorCode.OPENBANKING_REQUEST_FAILED;
    }

	// 관리자 코드
	private final ErrorCode errorCode;

	private OpenBankingException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public static OpenBankingException notConfigured() {
		return new OpenBankingException(ErrorCode.OPENBANKING_NOT_CONFIGURED);
	}

	public static OpenBankingException unavailable() {
		return new OpenBankingException(ErrorCode.OPENBANKING_UNAVAILABLE);
	}
}
