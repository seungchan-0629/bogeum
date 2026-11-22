package com.bogeum.domain.chat;

import com.bogeum.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class ChatException extends RuntimeException {
	private final ErrorCode errorCode;
	private ChatException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}
	public static ChatException agentNotAssigned() { return new ChatException(ErrorCode.CHAT_AGENT_NOT_ASSIGNED); }
	public static ChatException chatRoomNotFound() { return new ChatException(ErrorCode.CHAT_ROOM_NOT_FOUND); }
	public static ChatException chatRoomReadOnly() { return new ChatException(ErrorCode.CHAT_ROOM_READ_ONLY); }
	public static ChatException storageUnavailable() { return new ChatException(ErrorCode.CHAT_STORAGE_UNAVAILABLE); }
}
