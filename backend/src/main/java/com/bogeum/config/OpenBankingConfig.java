package com.bogeum.config;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.bogeum.domain.openbanking.support.OpenBankingClient;

@Configuration
@EnableConfigurationProperties(OpenBankingProperties.class)
public class OpenBankingConfig {

	@Bean
	public OpenBankingClient openBankingClient(OpenBankingProperties properties) {
		var httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
		var requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.timeout());
		var restClient = RestClient.builder().baseUrl(properties.baseUrl())
			.requestFactory(requestFactory).build();
		return new OpenBankingClient(restClient, properties);
	}
}
