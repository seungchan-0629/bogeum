package com.bogeum.domain.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 가입 전 휴대폰 인증. 번호별 한 행을 잠가 재발송·확인·가입의 중복 처리를 막는다. */
@Entity
@Table(name = "phone_verification", uniqueConstraints = {
	@UniqueConstraint(name = "uk_phone_verification_phone", columnNames = "phone"),
	@UniqueConstraint(name = "uk_phone_verification_id", columnNames = "verification_id"),
	@UniqueConstraint(name = "uk_phone_verification_token", columnNames = "verification_token")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhoneVerification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "phone_verification_id")
	private Long phoneVerificationId;

	@Column(nullable = false, length = 20)
	private String phone;

	@Column(name = "verification_id", length = 36)
	private String verificationId;

	@Column(name = "code_hash", length = 100)
	private String codeHash;

	@Column(name = "sent_at", columnDefinition = "TIMESTAMP(6)")
	private Instant sentAt;

	@Column(name = "expires_at", columnDefinition = "TIMESTAMP(6)")
	private Instant expiresAt;

	@Column(name = "failed_attempts", nullable = false)
	private int failedAttempts;

	@Column(name = "verified_at", columnDefinition = "TIMESTAMP(6)")
	private Instant verifiedAt;

	@Column(name = "verification_token", length = 36)
	private String verificationToken;

	@Column(name = "token_expires_at", columnDefinition = "TIMESTAMP(6)")
	private Instant tokenExpiresAt;

	@Column(name = "consumed_at", columnDefinition = "TIMESTAMP(6)")
	private Instant consumedAt;

	@Column(name = "send_window_started_at", columnDefinition = "TIMESTAMP(6)")
	private Instant sendWindowStartedAt;

	@Column(name = "send_count", nullable = false)
	private int sendCount;

	public PhoneVerification(String phone) {
		this.phone = phone;
	}

	/** 새 번호를 발급하면 이전 인증번호와 가입용 토큰을 모두 무효화한다. */
	public void issueCode(String verificationId, String codeHash, Instant now, Instant expiresAt) {
		if (sendWindowStartedAt == null || !now.isBefore(sendWindowStartedAt.plusSeconds(3600))) {
			sendWindowStartedAt = now;
			sendCount = 0;
		}
		sendCount++;
		this.verificationId = verificationId;
		this.codeHash = codeHash;
		this.sentAt = now;
		this.expiresAt = expiresAt;
		failedAttempts = 0;
		verifiedAt = null;
		verificationToken = null;
		tokenExpiresAt = null;
		consumedAt = null;
	}

	public void recordFailure() {
		failedAttempts++;
	}

	public void verify(String verificationToken, Instant now, Instant tokenExpiresAt) {
		this.verificationToken = verificationToken;
		this.verifiedAt = now;
		this.tokenExpiresAt = tokenExpiresAt;
	}

	public void consume(Instant now) {
		this.consumedAt = now;
	}
}
