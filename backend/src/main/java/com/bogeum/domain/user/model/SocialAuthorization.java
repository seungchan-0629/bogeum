package com.bogeum.domain.user.model;

import com.bogeum.common.enums.SocialProvider;
import java.io.Serializable;
import java.time.Instant;
import lombok.Getter;

/** 브라우저 세션에만 보관하는 일회용 인증 정보. 소셜 서비스의 토큰은 저장하지 않는다. */
@Getter
public class SocialAuthorization implements Serializable {

	private static final long serialVersionUID = 1L;
	private final SocialProvider provider;
	private String state;
	private final Instant expiresAt;
	private final Long userId;
	private String providerUserId;
	private String name;
	private String email;

	public SocialAuthorization(SocialProvider provider, String state, Instant expiresAt, Long userId) {
		this.provider = provider;
		this.state = state;
		this.expiresAt = expiresAt;
		this.userId = userId;
	}

	public void consumeState() {
		state = null;
	}

	public void authenticate(String providerUserId, String name, String email) {
		this.providerUserId = providerUserId;
		this.name = name;
		this.email = email;
	}
}
