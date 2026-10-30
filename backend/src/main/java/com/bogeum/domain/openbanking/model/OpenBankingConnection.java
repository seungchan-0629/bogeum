package com.bogeum.domain.openbanking.model;

import java.time.LocalDateTime;

import com.bogeum.domain.user.model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 로그인한 회원과 오픈뱅킹 인증을 연결한다. 토큰은 암호문만 저장한다. */
@Entity
@Table(name = "open_banking_connection")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenBankingConnection {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long openBankingConnectionId;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Column(length = 64)
	private String stateHash;
	private LocalDateTime stateExpiresAt;
	@Column(length = 20)
	private String userSeqNo;
	@Column(length = 2048)
	private String accessTokenEncrypted;
	@Column(length = 2048)
	private String refreshTokenEncrypted;
	private LocalDateTime tokenExpiresAt;

	public void beginAuthorization(String stateHash) {
		this.stateHash = stateHash;
		this.stateExpiresAt = LocalDateTime.now().plusMinutes(10);
	}

	public void authorize(String userSeqNo, String accessToken, String refreshToken, long expiresIn) {
		updateTokens(userSeqNo, accessToken, refreshToken, expiresIn);
		this.stateHash = null;
		this.stateExpiresAt = null;
	}

	public void updateTokens(String userSeqNo, String accessToken, String refreshToken, long expiresIn) {
		this.userSeqNo = userSeqNo;
		this.accessTokenEncrypted = accessToken;
		this.refreshTokenEncrypted = refreshToken;
		this.tokenExpiresAt = LocalDateTime.now().plusSeconds(expiresIn);
	}
}
