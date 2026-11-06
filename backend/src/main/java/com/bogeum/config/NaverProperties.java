package com.bogeum.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 네이버 키는 환경 변수로 받으며, 비어 있어도 다른 로그인은 사용할 수 있다. */
@ConfigurationProperties(prefix = "naver")
public record NaverProperties(String clientId, String clientSecret, String redirectUri, String frontendUrl) {

	public boolean isConfigured() {
		return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
	}
}
