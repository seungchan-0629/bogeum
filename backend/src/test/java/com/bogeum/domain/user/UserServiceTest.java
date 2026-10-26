package com.bogeum.domain.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.bogeum.common.enums.LoginProvider;
import com.bogeum.common.enums.UserStatus;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.user.model.User;

/** 실제 BCrypt 검증과 계정 상태 판단을 확인하며 DB 조회만 대체한다. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	// 테스트 속도를 위한 비용 값이며 운영 설정의 기본 비용에는 영향을 주지 않는다.
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
	private UserService userService;

	@BeforeEach
	void setUp() {
		userService = new UserService(userRepository, passwordEncoder);
	}

	@Test
	@DisplayName("해시와 일치하는 비밀번호만 성공하고 로그인 수단을 LOCAL로 변경한다")
	void loginWithMatchingPassword() {
		User user = storedUser(passwordEncoder.encode(" password "), UserStatus.ACTIVE);
		assertSame(user, userService.login("user@example.com", " password "));
		assertEquals(LoginProvider.LOCAL, user.getLastLoginProvider());
	}

	@Test
	@DisplayName("비밀번호가 다르면 로그인 수단을 변경하지 않는다")
	void rejectsWrongPassword() {
		User user = storedUser(passwordEncoder.encode("password"), UserStatus.ACTIVE);
		assertLoginFailed();
		assertEquals(LoginProvider.NAVER, user.getLastLoginProvider());
	}

	@Test
	@DisplayName("존재하지 않는 계정은 공통 로그인 실패로 처리한다")
	void rejectsMissingUser() {
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
		assertLoginFailed();
	}

	@Test
	@DisplayName("비밀번호 해시가 없는 소셜 전용 계정은 일반 로그인을 거절한다")
	void rejectsSocialOnlyUser() {
		storedUser(null, UserStatus.ACTIVE);
		assertLoginFailed();
	}

	@Test
	@DisplayName("비밀번호가 맞아도 탈퇴 계정은 로그인을 거절한다")
	void rejectsWithdrawnUser() {
		storedUser(passwordEncoder.encode("wrong-password"), UserStatus.WITHDRAWN);
		assertLoginFailed();
	}

	private User storedUser(String passwordHash, UserStatus userStatus) {
		User user = User.builder().email("user@example.com").passwordHash(passwordHash)
			.userStatus(userStatus).lastLoginProvider(LoginProvider.NAVER).build();
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		return user;
	}

	private void assertLoginFailed() {
		UserException exception = assertThrows(UserException.class,
			() -> userService.login("user@example.com", "wrong-password"));
		assertEquals(ErrorCode.LOGIN_FAILED, exception.getErrorCode());
	}
}
