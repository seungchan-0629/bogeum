package com.bogeum.common.exception;

import com.bogeum.domain.agent.AgentException;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.bogeum.domain.user.UserException;
import com.bogeum.domain.openbanking.OpenBankingException;

/** 컨트롤러의 검증 오류와 사용자 인증 실패를 공통 JSON 응답으로 변환한다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(OpenBankingException.class)
	public ResponseEntity<ErrorResponse> handleOpenBankingException(OpenBankingException exception) {
		return toResponse(exception.getErrorCode());
	}

	@ExceptionHandler(AgentException.class)
	public ResponseEntity<ErrorResponse> handleAgentException(AgentException exception) {
		return toResponse(exception.getErrorCode());
	}

	/** 서비스가 판단한 인증 실패를 401로 반환한다. */
	@ExceptionHandler(UserException.class)
	public ResponseEntity<ErrorResponse> handleUserException(UserException exception) {
		return toResponse(exception.getErrorCode());
	}

	/** 잘못된 JSON과 DTO 검증 실패는 비밀번호 원문 없이 400으로 반환한다. */
	@ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
	public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
		return toResponse(ErrorCode.INVALID_REQUEST);
	}

	private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode) {
		return ResponseEntity.status(errorCode.getHttpStatus()).body(new ErrorResponse(errorCode));
	}
}
