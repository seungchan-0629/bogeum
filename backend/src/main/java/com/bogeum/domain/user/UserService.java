package com.bogeum.domain.user;

import com.bogeum.common.enums.Role;
import com.bogeum.common.enums.UserStatus;
import com.bogeum.domain.user.UserRepository.RefreshTokenRepository;
import com.bogeum.utils.JwtUtil.RefreshToken;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.UUID;
import com.bogeum.utils.JwtUtil;
import com.bogeum.domain.user.model.User;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
	private final PasswordEncoder passwordEncoder;
	// 인증 관리자가 이 서비스를 사용자 조회에 사용하므로 로그인 시점에 가져온다.
	private final ObjectProvider<AuthenticationManager> authenticationManagerProvider;
	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final JwtUtil jwtUtil;

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
		return createAccount(email, password, name, phone, hasPrivacyConsent, hasThirdPartyConsent, Role.USER);
	}

	/** AgentService의 가입 트랜잭션에 참여하며 계정 역할은 서버에서 지정한다. */
	@Transactional
	public User signupAgent(@NotBlank @Email @Size(max = 255) String email,
			@NotBlank String password, @NotBlank @Size(max = 50) String name,
			@NotBlank @Size(max = 20) String phone,
			boolean hasPrivacyConsent, boolean hasThirdPartyConsent) {
		return createAccount(email, password, name, phone, hasPrivacyConsent, hasThirdPartyConsent, Role.AGENT);
	}

	private User createAccount(String email, String password, String name, String phone,
			boolean hasPrivacyConsent, boolean hasThirdPartyConsent, Role role) {
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
			.role(role)
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


	/** 비밀번호 인증과 최근 로그인 수단 갱신 후 토큰 발급을 요청한다. */
	@Transactional
	public AuthResult login(String email, String password) {
		if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw UserException.loginFailed();
		}
		LoginUser principal;
		try {
			var authentication = authenticationManagerProvider.getObject().authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(email, password));
			principal = (LoginUser) authentication.getPrincipal();
		} catch (AuthenticationException exception) {
			throw UserException.loginFailed();
		}
		User user = userRepository.findById(principal.getUserId()).orElseThrow(UserException::loginFailed);
		user.recordLocalLogin();
		Instant now = Instant.now();
		Instant expiresAt = jwtUtil.calculateRefreshExpiresAt(now);
		String sessionKey = UUID.randomUUID().toString();
		String refresh = jwtUtil.createRefresh(user.getUserId(), sessionKey, expiresAt);
		refreshTokenRepository.save(new RefreshToken(user, sessionKey, jwtUtil.hashToken(refresh), expiresAt, now));
		return new AuthResult(user, jwtUtil.createAccess(user.getUserId(), sessionKey), refresh, expiresAt);
	}

	/** 회원을 찾는다. 비밀번호 비교는 DaoAuthenticationProvider가 담당한다. */
	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String email) {
		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new UsernameNotFoundException("로그인할 수 없습니다."));
		if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
			// 소셜 전용 계정에는 일반 로그인용 해시가 없다.
			throw new UsernameNotFoundException("로그인할 수 없습니다.");
		}
		return new LoginUser(user);
	}

	/** R: 로그인 기록과 회원 상태를 확인해 로그아웃·탈퇴를 즉시 반영한다. */
	@Transactional(readOnly = true)
	public User findUserForSession(Long userId, String sessionKey) {
		RefreshToken token = refreshTokenRepository.findWithUser(sessionKey)
			.orElseThrow(UserException::required);
		if (!isUsable(token, userId)) {
			throw UserException.required();
		}
		return token.getUser();
	}

	@Transactional(readOnly = true)
	public User findUser(Long userId) {
		return userRepository.findById(userId).orElseThrow(UserException::required);
	}

	/** 현재 비밀번호를 확인한 뒤 새 해시를 저장한다. 빈 새 비밀번호는 기존 값을 유지한다. */
	@Transactional
	public User updateProfile(Long userId, String phone, String currentPassword,
			String newPassword, String passwordConfirm) {
		User user = findUser(userId);
		String normalizedPhone = phone == null ? null : phone.trim();
		if (normalizedPhone != null && (normalizedPhone.isBlank() || normalizedPhone.length() > 20)) {
			throw UserException.invalidProfile();
		}
		String passwordHash = null;
		if (newPassword != null && !newPassword.isEmpty()) {
			if (newPassword.isBlank() || newPassword.getBytes(StandardCharsets.UTF_8).length > 72
					|| !newPassword.equals(passwordConfirm)) {
				throw UserException.invalidProfile();
			}
			if (currentPassword == null || currentPassword.isBlank()
					|| currentPassword.getBytes(StandardCharsets.UTF_8).length > 72
					|| user.getPasswordHash() == null
					|| !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
				throw UserException.currentPasswordMismatch();
			}
			passwordHash = passwordEncoder.encode(newPassword);
		} else if (passwordConfirm != null && !passwordConfirm.isEmpty()) {
			throw UserException.invalidProfile();
		}
		// 모든 검증을 마친 후 함께 변경하여 실패한 요청이 전화번호만 저장하지 않게 한다.
		user.updateProfile(normalizedPhone, passwordHash);
		return user;
	}

	/** U: 갱신 때마다 원문을 교체한다. 최초 로그인 시 정한 최종 만료시각은 연장하지 않는다. */
	@Transactional
	public AuthResult refresh(String rawToken) {
		Claims claims = jwtUtil.validateRefreshToken(rawToken);
		RefreshToken token = refreshTokenRepository.findForUpdate(claims.get("sid", String.class))
			.orElseThrow(UserException::invalidRefresh);
		if (!isUsable(token, Long.valueOf(claims.getSubject())) || !jwtUtil.matchesTokenHash(rawToken, token.getTokenHash())) {
			throw UserException.invalidRefresh();
		}
		User user = token.getUser();
		String refresh = jwtUtil.createRefresh(user.getUserId(), token.getSessionKey(), token.getExpiresAt());
		token.rotate(jwtUtil.hashToken(refresh), Instant.now());
		return new AuthResult(user, jwtUtil.createAccess(user.getUserId(), token.getSessionKey()),
			refresh, token.getExpiresAt());
	}

	/** D: 해당 로그인 기록을 폐기한다. 잘못된/만료된 쿠키도 브라우저에서는 삭제할 수 있다. */
	@Transactional
	public void logout(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return;
		}
		Claims claims;
		try {
			claims = jwtUtil.validateRefreshToken(rawToken);
		} catch (UserException exception) {
			// 로그아웃은 이미 무효인 자격증명도 성공으로 처리하는 멱등 동작이다.
			return;
		}
		refreshTokenRepository.findForUpdate(claims.get("sid", String.class)).ifPresent(token -> {
			if (jwtUtil.matchesTokenHash(rawToken, token.getTokenHash())) {
				token.revoke(Instant.now());
			}
		});
	}

	private boolean isUsable(RefreshToken token, Long userId) {
		return token.isUsable(Instant.now())
			&& token.getUser().getUserStatus() == UserStatus.ACTIVE
			&& token.getUser().getUserId().equals(userId);
	}

	/** 서비스 내부 결과. Controller가 프론트에 전달할 토큰 응답 DTO로 변환한다. */
	public record AuthResult(User user, String accessToken, String refreshToken, Instant refreshExpiresAt) { }

}
