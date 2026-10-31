package com.bogeum.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.bogeum.domain.openbanking.OpenBankingClient;

@Configuration
@EnableConfigurationProperties(OpenBankingConfig.Properties.class)
public class OpenBankingConfig {

	@Bean
	public OpenBankingClient openBankingClient(Properties properties) {
		var httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
		var requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.timeout());
		var restClient = RestClient.builder().baseUrl(properties.baseUrl())
			.requestFactory(requestFactory).build();
		return new OpenBankingClient(restClient, properties);
	}

	/** 오픈뱅킹 비밀값은 환경 변수로만 공급한다. */
	@ConfigurationProperties(prefix = "openbanking")
	public record Properties(
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
		public Properties {
			baseUrl = baseUrl == null ? "https://testapi.openbanking.or.kr" : baseUrl;
			timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
		}
	}
}
