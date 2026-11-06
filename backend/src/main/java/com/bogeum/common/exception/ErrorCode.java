package com.bogeum.common.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
	// 종료된 AS의 댓글 추가·재개 요청을 거절한다 (AS_003).
	AS_ALREADY_COMPLETED(HttpStatus.CONFLICT, "처리가 완료된 AS 요청은 변경할 수 없습니다."),
	// 타인 소유 AS와 없는 AS를 구분하지 않는다 (AS_002).
	AS_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "AS 요청을 찾을 수 없습니다."),
	// AS 최초 접수의 계약 검증·사진 처리·저장소 연결 실패 (AS_001)
	AS_CONTRACT_NOT_FOUND(HttpStatus.NOT_FOUND, "계약을 찾을 수 없습니다."),
	AS_OUTSIDE_CONTRACT(HttpStatus.BAD_REQUEST, "계약 시작일부터 종료일까지 AS를 접수할 수 있습니다."),
	AS_INVALID_IMAGE(HttpStatus.BAD_REQUEST, "사진의 형식, 크기와 개수를 확인해 주세요."),
	AS_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "사진을 저장할 수 없습니다. 잠시 후 다시 시도해 주세요."),
	AGENT_NOT_FOUND(HttpStatus.NOT_FOUND, "중개사 정보를 찾을 수 없습니다."),
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청값을 확인해 주세요."),
	INVALID_LEASE_PRICE(HttpStatus.BAD_REQUEST, "임대 형식에 맞게 보증금과 월세를 입력해 주세요."),
	BROKERAGE_AGENT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "선택한 중개사에게 중개를 신청할 수 없습니다."),
	NO_BROKERAGE_AGENT(HttpStatus.CONFLICT, "지금은 중개를 맡을 수 있는 중개사가 없습니다."),
	BROKERAGE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "중개 신청을 찾을 수 없습니다."),
	BROKERAGE_ALREADY_ACCEPTED(HttpStatus.CONFLICT, "이미 수락한 중개 신청입니다."),
	INVALID_PROFILE(HttpStatus.BAD_REQUEST, "전화번호 또는 새 비밀번호와 확인값을 확인해 주세요. 비밀번호는 UTF-8 기준 72바이트까지 가능합니다."),
	CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호를 확인해 주세요."),
	INVALID_SIGNUP(HttpStatus.BAD_REQUEST, "가입 정보와 필수 동의를 확인해 주세요."),
	INVALID_PHONE(HttpStatus.BAD_REQUEST, "010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요."),
	SMS_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "문자 인증 서비스를 준비 중입니다. 잠시 후 다시 시도해 주세요."),
	SMS_SEND_FAILED(HttpStatus.BAD_GATEWAY, "인증 문자를 발송하지 못했습니다. 잠시 후 다시 시도해 주세요."),
	SMS_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "인증 문자는 60초 간격, 번호당 1시간에 5회까지 요청할 수 있습니다."),
	PHONE_VERIFICATION_INVALID(HttpStatus.BAD_REQUEST, "휴대폰 인증을 다시 요청해 주세요."),
	PHONE_VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST, "인증번호가 만료되었습니다. 다시 요청해 주세요."),
	PHONE_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않습니다."),
	PHONE_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인증번호를 5회 잘못 입력했습니다. 다시 요청해 주세요."),
	PHONE_VERIFICATION_REQUIRED(HttpStatus.BAD_REQUEST, "가입할 휴대폰 번호로 본인 인증을 완료해 주세요."),
	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
	SIGNUP_CONFLICT(HttpStatus.CONFLICT, "가입 정보를 저장할 수 없습니다."),
	LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해 주세요."),
	AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "인증 시간이 만료되었습니다."),
	ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "올바르지 않은 인증 정보입니다."),
	REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	CSRF_INVALID(HttpStatus.FORBIDDEN, "요청 확인 정보가 만료되었습니다. 새로고침해 주세요."),

	// 관리자 코드
	OPENBANKING_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "오픈뱅킹 기관 인증 정보가 설정되지 않았습니다."),
	OPENBANKING_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "가상금결원에 연결할 수 없습니다. 서버 실행 상태를 확인해 주세요."),
	OPENBANKING_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "금결원 요청이 거절되었습니다. 인증 정보와 서버 상태를 확인해 주세요.");

	private final HttpStatus httpStatus;
	private final String message;
}
