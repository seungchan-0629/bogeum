package com.bogeum.domain.user.model;

import com.bogeum.common.enums.ConsentType;
import com.bogeum.common.enums.LoginProvider;
import com.bogeum.common.enums.Role;
import com.bogeum.common.enums.UserStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * 사용자 (관리자, 임대인, 임차인, 중개사)
 */
@Entity
@Table(name = "user")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long userId;

	// 로그인 아이디. e-mail 형식만 가능하다 (USER_003)
	@Column(nullable = false, unique = true)
	private String email;

	// 원문은 저장하지 않는다. 소셜 로그인으로만 가입한 사용자는 비어 있다 (USER_008)
	private String passwordHash;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false, length = 20)
	private String phone;

	private String address;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Role role;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private UserStatus userStatus = UserStatus.ACTIVE;

	// 비어 있으면 미인증 (USER_003)
	private LocalDateTime identityVerifiedAt;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 20)
	private LoginProvider lastLoginProvider;

	// 가입 시 계정과 필수 동의를 같은 트랜잭션에서 저장한다.
	@Builder.Default
	@OneToMany(mappedBy = "user", cascade = CascadeType.PERSIST)
	private List<Consent> consents = new ArrayList<>();

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	/** 서비스에서 두 필수 동의를 확인한 후 가입 시 한 번 호출한다. */
	public void agreeToRequiredTerms() {
		consents.add(Consent.builder().user(this).consentType(ConsentType.PRIVACY_COLLECTION).build());
		consents.add(Consent.builder().user(this).consentType(ConsentType.THIRD_PARTY_PROVISION).build());
	}

	/** 일반 로그인 검증에 성공한 경우에만 최근 로그인 수단을 변경한다. */
	public void recordLocalLogin() {
		this.lastLoginProvider = LoginProvider.LOCAL;
	}

	/** 로그인 한 번당 하나의 갱신 기록. 원문 토큰 대신 SHA-256 해시만 저장한다. */
	@Entity(name = "RefreshToken")
	@Table(name = "refresh_token")
	@Getter
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	public static class RefreshToken {
		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		@Column(name = "refresh_token_id")
		private Long refreshTokenId;
		@ManyToOne(fetch = FetchType.LAZY, optional = false)
		@JoinColumn(name = "user_id", nullable = false)
		private User user;
		@Column(nullable = false, unique = true, length = 36)
		private String sessionKey;
		@Column(nullable = false, length = 64)
		private String tokenHash;
		@Enumerated(EnumType.STRING)
		@Column(nullable = false, length = 20)
		private RefreshTokenStatus refreshTokenStatus;
		@Column(nullable = false, columnDefinition = "TIMESTAMP")
		private Instant expiresAt;
		@Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
		private Instant createdAt;
		@Column(nullable = false, columnDefinition = "TIMESTAMP")
		private Instant updatedAt;

		public RefreshToken(User user, String sessionKey, String tokenHash, Instant expiresAt, Instant now) {
			this.user = user;
			this.sessionKey = sessionKey;
			this.tokenHash = tokenHash;
			this.expiresAt = expiresAt;
			this.createdAt = now;
			this.updatedAt = now;
			this.refreshTokenStatus = RefreshTokenStatus.ACTIVE;
		}
		public boolean isUsable(Instant now) {
			return refreshTokenStatus == RefreshTokenStatus.ACTIVE && expiresAt.isAfter(now);
		}
		public void rotate(String tokenHash, Instant now) {
			this.tokenHash = tokenHash;
			this.updatedAt = now;
		}
		public void revoke(Instant now) {
			this.refreshTokenStatus = RefreshTokenStatus.REVOKED;
			this.updatedAt = now;
		}

		public enum RefreshTokenStatus { ACTIVE, REVOKED }
	}
}
