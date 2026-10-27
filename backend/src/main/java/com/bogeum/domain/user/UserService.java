package com.bogeum.domain.user;

import java.nio.charset.StandardCharsets;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import com.bogeum.common.enums.Role;
import com.bogeum.common.enums.UserStatus;
import com.bogeum.domain.user.model.User;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@Service
@Validated
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	/**
	 * 일반 사용자의 계정과 필수 동의를 함께 저장한다.
	 * 이름·전화번호는 사용자가 입력한 정보이며 본인 인증 완료 시각은 채우지 않는다.
	 *
	 * @return 저장된 사용자. 비밀번호 해시가 있으므로 HTTP 응답으로 직접 반환하지 않는다.
	 * @throws UserException 가입 조건을 충족하지 않거나 저장이 충돌한 경우
	 */
	@Transactional
	public User signup(@NotBlank @Email @Size(max = 255) String email,
			@NotBlank String password, @NotBlank @Size(max = 50) String name,
			@NotBlank @Size(max = 20) String phone,
			boolean hasPrivacyConsent, boolean hasThirdPartyConsent) {
		if (!hasPrivacyConsent || !hasThirdPartyConsent
				|| password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw UserException.invalidSignup();
		}
		if (userRepository.findByEmail(email).isPresent()) {
			throw UserException.emailAlreadyExists();
		}

		User user = User.builder()
			.email(email)
			.passwordHash(passwordEncoder.encode(password))
			.name(name)
			.phone(phone)
			.role(Role.USER)
			.build();
		user.agreeToRequiredTerms();

		try {
			// 사전 조회 뒤 동시에 가입하는 경우도 DB 고유 제약으로 차단한다.
			return userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException exception) {
			// 동의 저장 실패도 함께 롤백한다. 모든 제약 위반을 이메일 중복으로 단정하지 않는다.
			throw UserException.signupConflict(exception);
		}
	}

	/**
	 * 이메일과 비밀번호를 검증하고 최근 로그인 수단을 갱신한다.
	 * HTTP 요청·응답 변환과 세션 생성은 컨트롤러 및 인증 설정의 책임이다.
	 *
	 * @param email 로그인 아이디인 이메일
	 * @param password 사용자가 입력한 원문 비밀번호
	 * @return 인증에 성공한 사용자
	 * @throws UserException 계정이 없거나 일반 로그인이 불가능한 경우
	 */
	@Transactional
	public User login(String email, String password) {
		// BCrypt 입력 한도를 넘으면 인코더 예외 대신 기존 인증 실패 응답을 반환한다.
		if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw UserException.loginFailed();
		}
		User user = userRepository.findByEmail(email).orElseThrow(UserException::loginFailed);

		// 소셜 전용 계정은 해시가 없다. 실패 사유를 통일해 계정 존재 여부를 노출하지 않는다.
		if (user.getUserStatus() != UserStatus.ACTIVE
				|| user.getPasswordHash() == null || user.getPasswordHash().isBlank()
				|| !passwordEncoder.matches(password, user.getPasswordHash())) {
			throw UserException.loginFailed();
		}

		// 관리 중인 엔티티이므로 트랜잭션 종료 시 변경 감지로 저장된다.
		user.recordLocalLogin();
		return user;
	}
}
