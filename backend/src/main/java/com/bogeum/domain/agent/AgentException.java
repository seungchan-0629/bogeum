package com.bogeum.domain.agent;

import com.bogeum.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class AgentException extends RuntimeException {
	private final ErrorCode errorCode;
	private AgentException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}
	public static AgentException notFound() { return new AgentException(ErrorCode.AGENT_NOT_FOUND); }
}
