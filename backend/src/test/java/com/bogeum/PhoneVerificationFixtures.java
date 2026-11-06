package com.bogeum;

import com.bogeum.domain.user.PhoneVerificationRepository;
import com.bogeum.domain.user.model.PhoneVerification;
import java.time.Instant;
import java.util.UUID;

/** 회원가입 자체를 검증하는 테스트에서만 사용하는 인증 완료 데이터. */
public final class PhoneVerificationFixtures {

	private PhoneVerificationFixtures() { }

	public static String verifiedToken(PhoneVerificationRepository repository, String phone) {
		PhoneVerification verification = repository.findAll().stream()
			.filter(candidate -> candidate.getPhone().equals(phone)).findFirst()
			.orElseGet(() -> new PhoneVerification(phone));
		Instant now = Instant.now();
		verification.issueCode(UUID.randomUUID().toString(), "unused-in-signup-test", now, now.plusSeconds(180));
		String token = UUID.randomUUID().toString();
		verification.verify(token, now, now.plusSeconds(600));
		repository.saveAndFlush(verification);
		return token;
	}
}
