package com.bogeum.domain.favorite;

import com.bogeum.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class FavoriteException extends RuntimeException {
	private final ErrorCode errorCode;
	private FavoriteException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}
	public static FavoriteException ownProperty() { return new FavoriteException(ErrorCode.FAVORITE_OWN_PROPERTY); }
}
