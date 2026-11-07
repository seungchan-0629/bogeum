package com.bogeum.config;

import com.bogeum.common.enums.Role;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 로컬 환경에서만 환경 변수로 지정한 관리자 계정을 한 번 생성한다. */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevAdminInitializer implements ApplicationRunner {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	@Value("${dev-admin.email:admin.dev@bogeum.local}")
	private String email;
	@Value("${dev-admin.password:}")
	private String password;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (password.isBlank() || userRepository.findByEmail(email).isPresent()) {
			return;
		}
		userRepository.save(User.builder()
			.email(email).passwordHash(passwordEncoder.encode(password))
			.name("개발 관리자").phone("01000000000").role(Role.ADMIN).build());
	}
}
