package com.bogeum.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모의 본인 인증의 토큰 해시, 대상 이메일, 인증 정보와 만료·소비 시각을 저장한다.
 */
@Entity
@Table(name = "identity_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentityVerification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false, length = 254)
	private String email;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false, length = 20)
	private String phoneNumber;

	@Column(nullable = false)
	private Instant expiresAt;

	private Instant consumedAt;

	public IdentityVerification(String tokenHash, String email, String name, String phoneNumber,
			Instant expiresAt) {
		this.tokenHash = tokenHash;
		this.email = email;
		this.name = name;
		this.phoneNumber = phoneNumber;
		this.expiresAt = expiresAt;
	}

	public boolean canConsume(String email, Instant now) {
		// 세 조건을 모두 만족해야 한다: 미사용, 만료 시각 이전, 발급 시 이메일과 일치.
		return consumedAt == null && expiresAt.isAfter(now) && this.email.equals(email);
	}

	public void consume(Instant now) {
		// 유효성 검사와 잠금은 호출 서비스에서 수행하고, 엔티티는 소비 시각을 기록한다.
		this.consumedAt = now;
	}
}
