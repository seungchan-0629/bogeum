package com.bogeum.domain;

/**
 * 계정 이용 상태: ACTIVE(정상), SUSPENDED(정지), WITHDRAWN(탈퇴). 인증과 현재 회원 조회는 ACTIVE만 허용한다.
 */
public enum AccountStatus {

	ACTIVE, SUSPENDED, WITHDRAWN
}
