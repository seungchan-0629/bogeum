package com.bogeum.domain.aftersales;

import com.bogeum.common.exception.ErrorCode;
import lombok.Getter;

/** AS 접수·조회 실패를 공통 오류 응답으로 전달한다. 임차인 불일치는 ACCESS_DENIED를 재사용한다. */
@Getter
public class AfterSalesException extends RuntimeException {
	private final ErrorCode errorCode;

	private AfterSalesException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public static AfterSalesException contractNotFound() { return new AfterSalesException(ErrorCode.AS_CONTRACT_NOT_FOUND); }
	// 조회 권한이 없는 접수도 같은 오류로 응답한다 (AS_002).
	public static AfterSalesException requestNotFound() { return new AfterSalesException(ErrorCode.AS_REQUEST_NOT_FOUND); }
	public static AfterSalesException alreadyCompleted() { return new AfterSalesException(ErrorCode.AS_ALREADY_COMPLETED); }
	public static AfterSalesException forbidden() { return new AfterSalesException(ErrorCode.ACCESS_DENIED); }
	public static AfterSalesException outsideContract() { return new AfterSalesException(ErrorCode.AS_OUTSIDE_CONTRACT); }
	public static AfterSalesException invalidImage() { return new AfterSalesException(ErrorCode.INVALID_IMAGE); }
	public static AfterSalesException storageUnavailable() { return new AfterSalesException(ErrorCode.AS_STORAGE_UNAVAILABLE); }
}
