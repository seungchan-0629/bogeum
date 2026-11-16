package com.bogeum.domain.movein;

import com.bogeum.common.exception.ErrorCode;
import lombok.Getter;

/** 입주 사진의 권한·기간·저장 실패를 공통 오류 응답으로 전달한다. */
@Getter
public class MoveInException extends RuntimeException {
	private final ErrorCode errorCode;

	private MoveInException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public static MoveInException contractNotFound() { return new MoveInException(ErrorCode.CONTRACT_NOT_FOUND); }
	public static MoveInException forbidden() { return new MoveInException(ErrorCode.ACCESS_DENIED); }
	public static MoveInException outsideUploadPeriod() { return new MoveInException(ErrorCode.MOVEIN_OUTSIDE_UPLOAD_PERIOD); }
	public static MoveInException invalidRequest() { return new MoveInException(ErrorCode.INVALID_REQUEST); }
	public static MoveInException invalidImage() { return new MoveInException(ErrorCode.INVALID_IMAGE); }
	public static MoveInException storageUnavailable() { return new MoveInException(ErrorCode.MOVEIN_STORAGE_UNAVAILABLE); }
}
