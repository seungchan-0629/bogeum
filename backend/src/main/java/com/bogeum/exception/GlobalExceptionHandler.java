package com.bogeum.exception;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 컨트롤러 및 그 안에서 호출한 서비스의 예외를 공통 JSON 오류 응답으로 변환한다.
 * 컨트롤러 이전의 보안 필터 오류는 SecurityConfig와 JsonResponseWriter에서 별도로 처리한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponseDTO> handleApi(ApiException exception) {
		return ResponseEntity.status(exception.getErrorCode().getStatus())
			.body(new ErrorResponseDTO(exception.getErrorCode(), exception.getFieldErrors()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException exception) {
		// account.email 같은 중첩 필드 경로를 유지해 프런트엔드가 해당 입력칸에 오류를 표시할 수 있게 한다.
		Map<String, String> errors = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
			errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
		return ResponseEntity.badRequest().body(new ErrorResponseDTO(ErrorCode.VALIDATION_FAILED, errors));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponseDTO> handleJson(HttpMessageNotReadableException exception) {
		return ResponseEntity.badRequest().body(new ErrorResponseDTO(ErrorCode.INVALID_JSON));
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponseDTO> handleMediaType(HttpMediaTypeNotSupportedException exception) {
		return ResponseEntity.status(415).body(new ErrorResponseDTO(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponseDTO> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		return ResponseEntity.badRequest().body(new ErrorResponseDTO(ErrorCode.VALIDATION_FAILED,
			Map.of(exception.getName(), "허용되지 않은 값입니다.")));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponseDTO> handleDataConflict(DataIntegrityViolationException exception) {
		// 사전 중복 검사 이후 동시 요청으로 발생한 DB 충돌도 처리하며, DB 오류 원문은 응답에 노출하지 않는다.
		String cause = String.valueOf(exception.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
		ErrorCode code = cause.contains("members") && cause.contains("email")
			? ErrorCode.EMAIL_ALREADY_EXISTS : ErrorCode.DATA_CONFLICT;
		return ResponseEntity.status(code.getStatus()).body(new ErrorResponseDTO(code));
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ErrorResponseDTO> handleAuthentication(AuthenticationException exception) {
		return ResponseEntity.status(ErrorCode.UNAUTHENTICATED.getStatus())
			.body(new ErrorResponseDTO(ErrorCode.UNAUTHENTICATED));
	}
}
