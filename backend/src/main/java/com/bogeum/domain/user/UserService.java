package com.bogeum.domain.user;

import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.common.enums.UserStatus;
import com.bogeum.domain.user.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

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
