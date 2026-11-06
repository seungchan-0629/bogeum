package com.bogeum.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(NaverProperties.class)
public class NaverConfig {

	@Bean
	public RestClient naverRestClient() {
		var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5)).build());
		factory.setReadTimeout(Duration.ofSeconds(5));
		return RestClient.builder().requestFactory(factory).build();
	}
}
