package com.bogeum.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 오픈뱅킹 비밀값은 환경 변수로만 공급한다. */
@ConfigurationProperties(prefix = "openbanking")
public record OpenBankingProperties(
	boolean enabled,
	String baseUrl,
	String clientId,
	String clientSecret,
	String redirectUri,
	String fintechOrgCode,
	String contractAccountNum,
	String depositPassPhrase,
	String encryptionKey,
	Duration timeout
) {
	public OpenBankingProperties {
		baseUrl = baseUrl == null ? "https://testapi.openbanking.or.kr" : baseUrl;
		timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
	}
}
