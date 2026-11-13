package com.bogeum.common.exception;

import com.bogeum.domain.agent.AgentException;
import com.bogeum.domain.aftersales.AfterSalesException;
import com.bogeum.domain.contract.ContractException;
import com.bogeum.domain.favorite.FavoriteException;
import com.bogeum.domain.property.PropertyException;
import com.bogeum.domain.openbanking.OpenBankingException;
import com.bogeum.domain.risk.RiskException;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.bogeum.domain.user.UserException;

/** 컨트롤러의 검증 오류와 사용자 인증 실패를 공통 JSON 응답으로 변환한다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(RiskException.class)
	public ResponseEntity<ErrorResponse> handleRiskException(RiskException exception) {
		return toResponse(exception.getErrorCode());
	}

	/** 서비스가 판단한 AS 실패 사유의 상태 코드와 메시지를 공통 JSON으로 반환한다. */
	@ExceptionHandler(AfterSalesException.class)
	public ResponseEntity<ErrorResponse> handleAfterSalesException(AfterSalesException exception) {
		return toResponse(exception.getErrorCode());
	}

	/** multipart 파싱 단계의 용량 초과도 현재 사진 오류 정책에 따라 400으로 반환한다. */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorResponse> handleUploadLimit(Exception exception) {
		return toResponse(ErrorCode.INVALID_IMAGE);
	}

	/** 공통 사진 검사에서 거절한 사진을 400으로 반환한다. */
	@ExceptionHandler(ImageException.class)
	public ResponseEntity<ErrorResponse> handleImageException(ImageException exception) {
		return toResponse(exception.getErrorCode());
	}

	@ExceptionHandler(AgentException.class)
	public ResponseEntity<ErrorResponse> handleAgentException(AgentException exception) {
		return toResponse(exception.getErrorCode());
	}

	@ExceptionHandler(PropertyException.class)
	public ResponseEntity<ErrorResponse> handlePropertyException(PropertyException exception) {
		return toResponse(exception.getErrorCode());
	}

	@ExceptionHandler(ContractException.class)
	public ResponseEntity<ErrorResponse> handleContractException(ContractException exception) {
		return toResponse(exception.getErrorCode());
	}

	@ExceptionHandler(FavoriteException.class)
	public ResponseEntity<ErrorResponse> handleFavoriteException(FavoriteException exception) {
		return toResponse(exception.getErrorCode());
	}

	/** 서비스가 판단한 인증 실패를 401로 반환한다. */
	@ExceptionHandler(UserException.class)
	public ResponseEntity<ErrorResponse> handleUserException(UserException exception) {
		return toResponse(exception.getErrorCode());
	}

	/** 잘못된 JSON과 DTO 검증 실패, 빠지거나 형식이 틀린 요청 파라미터는 비밀번호 원문 없이 400으로 반환한다. */
	@ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
		MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
	public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
		return toResponse(ErrorCode.INVALID_REQUEST);
	}

	private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode) {
		return ResponseEntity.status(errorCode.getHttpStatus()).body(new ErrorResponse(errorCode));
	}

	// 관리자 코드
	@ExceptionHandler(OpenBankingException.class)
	public ResponseEntity<ErrorResponse> handleOpenBankingException(OpenBankingException exception) {
		return toResponse(exception.getErrorCode());
	}
}
