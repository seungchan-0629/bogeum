package com.bogeum.config;

import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.service.DefaultMessageService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 문자 발송 클라이언트를 한 번 생성하여 서비스에 주입한다. */
@Configuration
@EnableConfigurationProperties(SolapiProperties.class)
public class SolapiConfig {

	@Bean
	public DefaultMessageService messageService(SolapiProperties properties) {
		return SolapiClient.INSTANCE.createInstance(properties.apiKey(), properties.apiSecret());
	}
}
