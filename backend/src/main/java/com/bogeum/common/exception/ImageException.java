package com.bogeum.common.exception;

import lombok.Getter;

/** 여러 기능이 함께 쓰는 사진 검사(ImageProcessor) 실패를 공통 오류 응답으로 전달한다. */
@Getter
public class ImageException extends RuntimeException {
	private final ErrorCode errorCode;

	private ImageException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public static ImageException invalidImage() { return new ImageException(ErrorCode.INVALID_IMAGE); }
}
