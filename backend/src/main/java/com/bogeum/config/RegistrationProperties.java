package com.bogeum.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * bogeum.registration 설정을 회원가입 서비스에 전달한다.
 * 가입 허용 여부, 회원 유형별 약관 버전, 중개사 서류 제출 기한을 관리한다.
 */
@Component
@ConfigurationProperties(prefix = "bogeum.registration")
@Validated
@Getter
@Setter
public class RegistrationProperties {

	private boolean enabled = false;

	@NotBlank
	@Size(max = 50)
	private String generalTermsVersion = "general-dev-v1";

	@NotBlank
	@Size(max = 50)
	private String agentTermsVersion = "agent-dev-v1";

	@Min(1)
	@Max(30)
	private int documentSubmissionDays = 3;
}
