package com.bogeum.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.bogeum.dto.VerifiedIdentityDTO;
import com.bogeum.exception.ApiException;
import com.bogeum.exception.ErrorCode;

/**
 * dev/test 외 환경에서 선택되는 구현체. 실제 인증 연동 전에는 인증 증빙 사용을 거절한다.
 */
@Service
@Profile("!dev & !test")
public class UnavailableIdentityVerificationService implements IdentityVerificationService {

	@Override
	public VerifiedIdentityDTO consume(String token, String email) {
		throw new ApiException(ErrorCode.IDENTITY_VERIFICATION_UNAVAILABLE);
	}
}
