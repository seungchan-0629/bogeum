package com.bogeum.domain.user;

import com.bogeum.common.enums.Role;
import com.bogeum.common.enums.UserStatus;
import com.bogeum.config.SolapiProperties;
import com.bogeum.domain.user.model.PhoneVerification;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import io.jsonwebtoken.Claims;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
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
import com.bogeum.domain.user.model.UserDto.AuthResult;

@Service
@Validated
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private final PasswordEncoder passwordEncoder;
	// 인증 관리자가 이 서비스를 사용자 조회에 사용하므로 로그인 시점에 가져온다.
	private final ObjectProvider<AuthenticationManager> authenticationManagerProvider;
	private final UserRepository userRepository;
	private final JwtUtil jwtUtil;
	private final PhoneVerificationRepository phoneVerificationRepository;
	private final DefaultMessageService messageService;
	private final SolapiProperties solapiProperties;

	/**
	 * 일반 사용자의 계정과 필수 동의를 함께 저장한다.
	 * 같은 전화번호의 인증 토큰을 확인하고 가입과 함께 한 번만 사용 처리한다.
	 *
	 * @return 저장된 사용자. 비밀번호 해시가 있으므로 HTTP 응답으로 직접 반환하지 않는다.
	 * @throws UserException 가입 조건을 충족하지 않거나 저장이 충돌한 경우
	 */
	@Transactional
	public User signup(@NotBlank @Email @Size(max = 255) String email,
			@NotBlank String password, @NotBlank @Size(max = 50) String name,
			@NotBlank @Size(max = 20) String phone,
			boolean hasPrivacyConsent, boolean hasThirdPartyConsent, String verificationToken) {
		String normalizedPhone = normalizePhone(phone);
		if (verificationToken == null || verificationToken.isBlank()) {
			throw UserException.phoneVerificationRequired();
		}
		PhoneVerification verification = phoneVerificationRepository.findByVerificationToken(verificationToken)
			.orElseThrow(UserException::phoneVerificationRequired);
		Instant now = Instant.now();
		if (!verification.getPhone().equals(normalizedPhone) || verification.getVerifiedAt() == null
				|| verification.getConsumedAt() != null || !now.isBefore(verification.getTokenExpiresAt())) {
			throw UserException.phoneVerificationRequired();
		}
		User user = createAccount(email, password, name, normalizedPhone, hasPrivacyConsent, hasThirdPartyConsent, Role.USER);
		user.verifyIdentity(LocalDateTime.ofInstant(verification.getVerifiedAt(), ZoneId.systemDefault()));
		verification.consume(now);
		return user;
	}

	/** 번호별 발송 제한을 확인하고 인증번호의 해시를 저장한 뒤 SOLAPI로 발송한다. */
	@Transactional
	public PhoneVerification requestIdentityVerification(String phone) {
		String normalizedPhone = normalizePhone(phone);
		if (!solapiProperties.isConfigured()) {
			throw UserException.smsNotConfigured();
		}
		PhoneVerification verification = phoneVerificationRepository.findByPhone(normalizedPhone).orElse(null);
		Instant now = Instant.now();
		if (verification == null) {
			try {
				verification = phoneVerificationRepository.saveAndFlush(new PhoneVerification(normalizedPhone));
			} catch (DataIntegrityViolationException exception) {
				// 같은 번호로 처음 요청한 두 호출이 동시에 발송되는 것을 막는다.
				throw UserException.smsRateLimited();
			}
		}
		if (verification.getSentAt() != null && now.isBefore(verification.getSentAt().plusSeconds(60))
				|| verification.getSendWindowStartedAt() != null
				&& now.isBefore(verification.getSendWindowStartedAt().plusSeconds(3600))
				&& verification.getSendCount() >= 5) {
			throw UserException.smsRateLimited();
		}
		String code = String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
		verification.issueCode(UUID.randomUUID().toString(), passwordEncoder.encode(code), now, now.plusSeconds(180));
		Message message = new Message();
		message.setFrom(solapiProperties.senderNumber());
		message.setTo(normalizedPhone);
		message.setText("[보금] 인증번호 [" + code + "]를 3분 안에 입력해 주세요.");
		try {
			var response = messageService.send(message);
			if (response == null || !response.getFailedMessageList().isEmpty()) {
				throw new IllegalStateException("인증 문자 발송이 접수되지 않았습니다.");
			}
		} catch (Exception exception) {
			// 발송이 실패하면 새 인증번호와 발송 제한 변경도 함께 롤백한다.
			throw UserException.smsSendFailed(exception);
		}
		return verification;
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


	/** 탈퇴한 회원이 기존 토큰을 계속 사용하지 못하도록 활성 회원만 조회한다. */
	@Transactional(readOnly = true)
	public User findUser(Long userId) {
		User user = userRepository.findById(userId).orElseThrow(UserException::required);
		if (user.getUserStatus() != UserStatus.ACTIVE) {
			throw UserException.required();
		}
		return user;
	}

	/** 오입력 횟수는 실패 응답에도 커밋하여 반복 추측을 제한한다. */
	@Transactional(noRollbackFor = UserException.class)
	public PhoneVerification confirmIdentityVerification(String verificationId, String code) {
		PhoneVerification verification = phoneVerificationRepository.findByVerificationId(verificationId)
			.orElseThrow(UserException::phoneVerificationInvalid);
		Instant now = Instant.now();
		if (verification.getVerifiedAt() != null || verification.getConsumedAt() != null) {
			throw UserException.phoneVerificationInvalid();
		}
		if (!now.isBefore(verification.getExpiresAt())) {
			throw UserException.phoneVerificationExpired();
		}
		if (verification.getFailedAttempts() >= 5) {
			throw UserException.phoneAttemptsExceeded();
		}
		if (code == null || !code.matches("\\d{6}") || !passwordEncoder.matches(code, verification.getCodeHash())) {
			verification.recordFailure();
			if (verification.getFailedAttempts() >= 5) {
				throw UserException.phoneAttemptsExceeded();
			}
			throw UserException.phoneCodeMismatch();
		}
		verification.verify(UUID.randomUUID().toString(), now, now.plusSeconds(600));
		return verification;
	}

	private String normalizePhone(String phone) {
		String normalizedPhone = phone == null ? "" : phone.replaceAll("[\\s-]", "");
		if (!normalizedPhone.matches("010\\d{8}")) {
			throw UserException.invalidPhone();
		}
		return normalizedPhone;
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

	/** Refresh 토큰과 만료시각은 유지하고 새 Access 토큰을 발급한다. */
	@Transactional(readOnly = true)
	public AuthResult refresh(String rawToken) {
		Claims claims = jwtUtil.validateRefreshToken(rawToken);

		Long userId = Long.valueOf(claims.getSubject());
		User user = userRepository.findById(userId)
				.orElseThrow(UserException::invalidRefresh);

		if(user.getUserStatus() != UserStatus.ACTIVE)
		{
			throw UserException.invalidRefresh();
		}

		String sessionKey = claims.get("sid", String.class);
		String accessToken = jwtUtil.createAccess(userId, sessionKey);
		Instant refreshExpiresAt = claims.getExpiration().toInstant();

		return new AuthResult(user,accessToken,rawToken,refreshExpiresAt);
	}

}
