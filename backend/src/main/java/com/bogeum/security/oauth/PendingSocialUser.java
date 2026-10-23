package com.bogeum.security.oauth;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import com.bogeum.exception.ApiException;
import com.bogeum.exception.ErrorCode;

/** 소셜 인증만 끝난 임시 주체. 가입 전에는 일반 회원 API 권한이 없다. */
public record PendingSocialUser(SocialProfile profile, Instant expiresAt) implements OAuth2User, Serializable {
	public PendingSocialUser(SocialProfile profile) {
		this(profile, Instant.now().plusSeconds(600));
	}

	public void requireValid() {
		if (!Instant.now().isBefore(expiresAt)) {
			throw new ApiException(ErrorCode.SOCIAL_REGISTRATION_EXPIRED);
		}
	}

	@Override
	public String getName() {
		return profile.provider() + ":" + profile.providerUserId();
	}

	@Override
	public Map<String, Object> getAttributes() {
		return Map.of("provider", profile.provider());
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_SOCIAL_PENDING"));
	}
}
