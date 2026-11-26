package com.bogeum.domain.notification;

import com.bogeum.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class NotificationException extends RuntimeException {
	private final ErrorCode errorCode;
	private NotificationException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}
	public static NotificationException notificationNotFound() { return new NotificationException(ErrorCode.NOTIFICATION_NOT_FOUND); }
	public static NotificationException invalidRequest() { return new NotificationException(ErrorCode.INVALID_REQUEST); }
}
