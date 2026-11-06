package com.bogeum.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 카카오 REST API 키와 시크릿은 환경 변수로 받는다. */
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(String clientId, String clientSecret, String redirectUri, String frontendUrl) {

	public boolean isConfigured() {
		return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
	}
}
