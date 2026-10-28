package com.bogeum.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 회원가입의 해시 생성과 로그인의 해시 검증에 사용할 인코더 설정. */
@Configuration
public class PasswordConfig {

	/** 원문 비밀번호를 DB에 저장하지 않도록 BCrypt를 사용한다. */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
