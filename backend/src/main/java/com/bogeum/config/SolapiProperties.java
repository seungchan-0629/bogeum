package com.bogeum.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** SOLAPI 접속 정보와 등록된 발신번호. 실제 값은 환경 변수로 전달한다. */
@ConfigurationProperties(prefix = "solapi")
public record SolapiProperties(String apiKey, String apiSecret, String senderNumber) {

	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank() && !apiKey.equals("API 키 입력")
			&& apiSecret != null && !apiSecret.isBlank() && !apiSecret.equals("API 시크릿 키 입력")
			&& senderNumber != null && senderNumber.matches("\\d{8,11}");
	}
}
