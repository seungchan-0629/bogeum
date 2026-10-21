package com.bogeum.config;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.domain.Member;
import com.bogeum.domain.MemberType;
import com.bogeum.repository.MemberRepository;

/**
 * dev 환경에서 로그인 확인용 계정을 준비한다.
 * 환경 변수로 개발 비밀번호가 제공된 경우에만 실행하며, 기존 계정은 덮어쓰지 않는다.
 */
@Component
@Profile("dev")
public class DevMemberInitializer implements CommandLineRunner {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final String password;

	public DevMemberInitializer(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
			@Value("${bogeum.dev.login-password:}") String password) {
		this.memberRepository = memberRepository;
		this.passwordEncoder = passwordEncoder;
		this.password = password;
	}

	@Override
	@Transactional
	public void run(String... args) {
		// 개발 비밀번호를 지정하지 않으면 계정을 만들지 않는다. 만들어지는 계정에는 가입 프로필·동의가 없다.
		if (password.isBlank()) {
			return;
		}
		if (password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new IllegalArgumentException("BOGEUM_DEV_PASSWORD must be at least 8 characters and at most 72 UTF-8 bytes");
		}
		createIfMissing("member@example.com", MemberType.GENERAL);
		createIfMissing("agent@example.com", MemberType.AGENT);
	}

	private void createIfMissing(String email, MemberType memberType) {
		if (!memberRepository.existsByEmail(email)) {
			memberRepository.save(new Member(email, passwordEncoder.encode(password), memberType));
		}
	}
}
