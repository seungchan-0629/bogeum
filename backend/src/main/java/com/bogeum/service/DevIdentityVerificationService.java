package com.bogeum.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.domain.IdentityVerification;
import com.bogeum.dto.IdentityVerificationRequestDTO;
import com.bogeum.dto.IdentityVerificationResponseDTO;
import com.bogeum.dto.VerifiedIdentityDTO;
import com.bogeum.exception.ApiException;
import com.bogeum.exception.ErrorCode;
import com.bogeum.repository.IdentityVerificationRepository;
import com.bogeum.security.EmailNormalizer;

import lombok.RequiredArgsConstructor;

/**
 * 외부 인증 대신 dev/test에서 사용할 일회용 본인 인증 토큰을 발급·소비한다.
 * 토큰은 이메일과 연결되며, 일반 회원가입 트랜잭션 안에서 한 번만 사용할 수 있다.
 */
@Service
@Profile({"dev", "test"})
@RequiredArgsConstructor
public class DevIdentityVerificationService implements IdentityVerificationService {

	private final IdentityVerificationRepository verificationRepository;
	private final SecureRandom random = new SecureRandom();

	@Transactional
	public IdentityVerificationResponseDTO issue(IdentityVerificationRequestDTO request) {
		// 예측하기 어려운 토큰 원문은 응답으로만 전달하고, DB에는 조회용 SHA-256 해시를 저장한다.
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		// 개발용 증빙도 10분 후에는 사용할 수 없으며, 다른 이메일의 가입에 사용할 수 없다.
		Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);
		verificationRepository.save(new IdentityVerification(hash(token),
			EmailNormalizer.normalize(request.getEmail()), request.getName().strip(),
			request.getPhoneNumber(), expiresAt));
		return new IdentityVerificationResponseDTO(token, expiresAt);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public VerifiedIdentityDTO consume(String token, String email) {
		if (token == null || token.isBlank()) {
			throw new ApiException(ErrorCode.INVALID_IDENTITY_VERIFICATION);
		}
		// 비관적 쓰기 잠금으로 같은 토큰을 소비하려는 요청들을 순차 처리한다.
		IdentityVerification verification = verificationRepository.findForConsumption(hash(token))
			.orElseThrow(() -> new ApiException(ErrorCode.INVALID_IDENTITY_VERIFICATION));
		Instant now = Instant.now();
		if (!verification.canConsume(EmailNormalizer.normalize(email), now)) {
			throw new ApiException(ErrorCode.INVALID_IDENTITY_VERIFICATION);
		}
		// DB 잠금을 잡고 같은 가입 트랜잭션 안에서 소비해 동시 재사용과 부분 저장을 막는다.
		// 조회한 엔티티의 변경은 JPA 변경 감지로 반영되며, 뒤의 가입 단계 실패 시 소비 기록도 롤백된다.
		verification.consume(now);
		return new VerifiedIdentityDTO(verification.getName(), verification.getPhoneNumber());
	}

	private String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
