package com.bogeum.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.bogeum.domain.user.UserException;
import com.bogeum.domain.user.model.User;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** JWT 문자열 생성·검증, 유효기간 계산과 토큰 해시 연산을 담당한다. */
@Component
public class JwtUtil {
	// 수업 예제용 고정 키와 토큰 설정.
	private static final String SECRET = "12345678901234567890123456789012345678901234567890";
	private static final String ISSUER = "bogeum";
	private static final Duration ACCESS_TTL = Duration.ofMinutes(10);
	private static final Duration REFRESH_TTL = Duration.ofDays(1);
	private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

	public String createAccess(Long userId, String sessionKey) {
		return create(userId, sessionKey, "access", Instant.now().plus(ACCESS_TTL));
	}
	public String createRefresh(Long userId, String sessionKey, Instant expiresAt) {
		return create(userId, sessionKey, "refresh", expiresAt);
	}
	public Instant calculateRefreshExpiresAt(Instant issuedAt) {
		return issuedAt.plus(REFRESH_TTL);
	}

	public Claims validateAccessToken(String token) {
		return validateToken(token, "access");
	}
	public Claims validateRefreshToken(String token) {
		return validateToken(token, "refresh");
	}
	private String create(Long userId, String sessionKey, String type, Instant expiresAt) {
		return Jwts.builder().issuer(ISSUER).subject(userId.toString())
			.id(UUID.randomUUID().toString()).claim("sid", sessionKey).claim("tokenType", type)
			.issuedAt(Date.from(Instant.now())).expiration(Date.from(expiresAt))
			.signWith(key, Jwts.SIG.HS256).compact();
	}
	private Claims validateToken(String token, String type) {
		try {
			// 토큰 용도도 확인한다. Refresh Token을 Access Token 대신 쓸 수 없다.
			Claims claims = Jwts.parser().verifyWith(key).requireIssuer(ISSUER)
				.require("tokenType", type).build().parseSignedClaims(token).getPayload();
			if (claims.getExpiration() == null || claims.getSubject() == null
					|| claims.get("sid", String.class) == null) {
				throw new IllegalArgumentException("필수 클레임 누락");
			}
			// 회원 번호 변환 오류도 잘못된 토큰으로 처리한다.
			Long.parseLong(claims.getSubject());
			return claims;
		} catch (ExpiredJwtException exception) {
			throw type.equals("access") ? UserException.expired() : UserException.invalidRefresh();
		} catch (JwtException | IllegalArgumentException exception) {
			throw type.equals("access") ? UserException.invalidAccess() : UserException.invalidRefresh();
		}
	}

	/** 난수 토큰의 DB 저장용 SHA-256 해시. 비밀번호용 BCrypt와 용도가 다르다. */
	public String hashToken(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
		}
	}

	public boolean matchesTokenHash(String token, String hash) {
		return MessageDigest.isEqual(hashToken(token).getBytes(StandardCharsets.US_ASCII),
			hash.getBytes(StandardCharsets.US_ASCII));
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
