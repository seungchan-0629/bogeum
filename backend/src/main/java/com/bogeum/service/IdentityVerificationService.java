package com.bogeum.service;

import com.bogeum.dto.VerifiedIdentityDTO;

/**
 * 가입 이메일에 해당하는 인증 증빙을 소비하고, 프로필에 저장할 이름·전화번호를 반환한다.
 */
public interface IdentityVerificationService {

	VerifiedIdentityDTO consume(String token, String email);
}
