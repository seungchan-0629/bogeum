package com.bogeum.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 인증 증빙 소비 후 일반 회원 프로필 생성에 전달하는 내부 데이터. 인증 기록의 이름·전화번호를 담는다.
 */
@Getter
@AllArgsConstructor
public class VerifiedIdentityDTO {

	private final String name;
	private final String phoneNumber;
}
