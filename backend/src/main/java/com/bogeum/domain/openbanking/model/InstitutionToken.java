package com.bogeum.domain.openbanking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "institution_token")
@Getter
@NoArgsConstructor
public class InstitutionToken {

	@Id
	@Column(name = "institution_token_id")
	private Long institutionTokenId = 1L;
	@Column(nullable = false, length = 2048)
	private String accessToken;
	@Column(nullable = false)
	private String clientUseCode;
	@Column(nullable = false, columnDefinition = "TIMESTAMP")
	private LocalDateTime issuedAt;
	@Column(nullable = false, columnDefinition = "TIMESTAMP")
	private LocalDateTime expiresAt;

	public void renew(String accessToken, String clientUseCode, long expiresIn) {
		this.accessToken = accessToken;
		this.clientUseCode = clientUseCode;
		this.issuedAt = LocalDateTime.now();
		this.expiresAt = issuedAt.plusSeconds(expiresIn);
	}

	public boolean isUsable() {
		return expiresAt != null && expiresAt.isAfter(LocalDateTime.now());
	}
}
