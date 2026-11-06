package com.bogeum.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 구글 OAuth 클라이언트 ID와 시크릿은 환경 변수로 받는다. */
@ConfigurationProperties(prefix = "google")
public record GoogleProperties(String clientId, String clientSecret, String redirectUri, String frontendUrl) {

	public boolean isConfigured() {
		return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
	}
}
