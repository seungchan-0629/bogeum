package com.bogeum.common.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
	OPEN_BANKING_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "오픈뱅킹 연결을 확인해 주세요."),
	OPEN_BANKING_NOT_CONNECTED(HttpStatus.CONFLICT, "오픈뱅킹 계좌를 먼저 연결해 주세요."),
	OPEN_BANKING_AUTH_INVALID(HttpStatus.BAD_REQUEST, "계좌 인증을 다시 시작해 주세요."),
	PAYMENT_ACCOUNT_REQUIRED(HttpStatus.CONFLICT, "출금에 동의한 본인 계좌를 등록해 주세요."),
	RENT_PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "월세 납부 정보를 찾을 수 없습니다."),
	CONTRACT_NOT_FOUND(HttpStatus.NOT_FOUND, "계약 정보를 찾을 수 없습니다."),
	INVALID_RENT_SCHEDULE(HttpStatus.BAD_REQUEST, "계약 기간과 월세 금액·납부일을 확인해 주세요."),
	VERIFIED_SETTLEMENT_ACCOUNT_REQUIRED(HttpStatus.CONFLICT, "임대인이 은행 검증을 마친 정산 계좌를 먼저 등록해야 합니다."),
	PAYMENT_IN_PROGRESS(HttpStatus.CONFLICT, "이미 요청한 이체입니다. 처리 결과를 확인해 주세요."),
	INVALID_PAYMENT_AMOUNT(HttpStatus.BAD_REQUEST, "이체 금액과 납부일을 확인해 주세요."),
	SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "정산 정보를 찾을 수 없습니다."),
	INVALID_SETTLEMENT_ACCOUNT(HttpStatus.BAD_REQUEST, "은행 코드와 본인 명의의 정산 계좌를 확인해 주세요."),
	AGENT_NOT_FOUND(HttpStatus.NOT_FOUND, "중개사 정보를 찾을 수 없습니다."),
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청값을 확인해 주세요."),
	INVALID_PROFILE(HttpStatus.BAD_REQUEST, "전화번호 또는 새 비밀번호와 확인값을 확인해 주세요. 비밀번호는 UTF-8 기준 72바이트까지 가능합니다."),
	CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호를 확인해 주세요."),
	INVALID_SIGNUP(HttpStatus.BAD_REQUEST, "가입 정보와 필수 동의를 확인해 주세요."),
	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
	SIGNUP_CONFLICT(HttpStatus.CONFLICT, "가입 정보를 저장할 수 없습니다."),
	LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해 주세요."),
	AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "인증 시간이 만료되었습니다."),
	ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "올바르지 않은 인증 정보입니다."),
	REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	CSRF_INVALID(HttpStatus.FORBIDDEN, "요청 확인 정보가 만료되었습니다. 새로고침해 주세요.");
	private final HttpStatus httpStatus;
	private final String message;
}
