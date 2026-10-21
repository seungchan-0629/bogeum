package com.bogeum.domain;

/**
 * 계정 및 보안 권한 구분: 일반 회원(GENERAL), 중개사(AGENT), 관리자(ADMIN). 공개 가입 API는 앞의 두 유형만 생성한다.
 */
public enum MemberType {

	GENERAL, AGENT, ADMIN
}
