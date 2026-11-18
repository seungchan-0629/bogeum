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
}
