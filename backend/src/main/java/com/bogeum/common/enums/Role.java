package com.bogeum.common.enums;

/**
 * 사용자 역할
 */
public enum Role {
	ADMIN, // 관리자
	USER, // 일반 사용자. 임대인·임차인 구분은 계약에서 관리한다.
	LANDLORD, // 임대인
	TENANT, // 임차인
	AGENT // 중개사
}
