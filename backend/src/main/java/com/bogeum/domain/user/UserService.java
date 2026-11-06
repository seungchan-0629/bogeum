package com.bogeum.domain.user;

import com.bogeum.common.enums.Role;
import com.bogeum.common.enums.LoginProvider;
import com.bogeum.common.enums.UserStatus;
import com.bogeum.common.enums.SocialProvider;
import com.bogeum.config.NaverProperties;
import com.bogeum.config.KakaoProperties;
import com.bogeum.config.SolapiProperties;
import com.bogeum.domain.user.model.SocialAuthorization;
import com.bogeum.domain.user.model.PhoneVerification;
import com.bogeum.domain.user.model.SocialAccount;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import io.jsonwebtoken.Claims;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Base64;
import java.util.UUID;
import com.bogeum.utils.JwtUtil;
import com.bogeum.domain.user.model.User;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.bogeum.domain.user.model.UserDto.AuthResult;

@Service
@Validated
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
	private final SocialAccountRepository socialAccountRepository;
	private final NaverProperties naverProperties;
	private final RestClient naverRestClient;
	private final KakaoProperties kakaoProperties;
	private final RestClient kakaoRestClient;

	public UserService(PasswordEncoder passwordEncoder, ObjectProvider<AuthenticationManager> authenticationManagerProvider,
			UserRepository userRepository, JwtUtil jwtUtil, PhoneVerificationRepository phoneVerificationRepository,
			DefaultMessageService messageService, SolapiProperties solapiProperties,
			SocialAccountRepository socialAccountRepository, NaverProperties naverProperties,
			@Qualifier("naverRestClient") RestClient naverRestClient, KakaoProperties kakaoProperties,
			@Qualifier("kakaoRestClient") RestClient kakaoRestClient) {
		this.passwordEncoder = passwordEncoder;
		this.authenticationManagerProvider = authenticationManagerProvider;
		this.userRepository = userRepository;
		this.jwtUtil = jwtUtil;
		this.phoneVerificationRepository = phoneVerificationRepository;
		this.messageService = messageService;
		this.solapiProperties = solapiProperties;
		this.socialAccountRepository = socialAccountRepository;
		this.naverProperties = naverProperties;
		this.naverRestClient = naverRestClient;
		this.kakaoProperties = kakaoProperties;
		this.kakaoRestClient = kakaoRestClient;
	}

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
		PhoneVerification verification = verifiedPhone(normalizedPhone, verificationToken);
		Instant now = Instant.now();
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

	/** userId가 있으면 로그인한 일반 회원의 연결용 인증을 시작한다. */
	@Transactional(readOnly = true)
	public SocialAuthorization createSocialAuthorization(SocialProvider provider, Long userId) {
		if (userId != null) {
			regularUser(userId);
			if (socialAccountRepository.existsByUserUserIdAndProvider(userId, provider)) {
				throw UserException.socialAlreadyConnected();
			}
		}
		byte[] random = new byte[32];
		SECURE_RANDOM.nextBytes(random);
		return new SocialAuthorization(provider, Base64.getUrlEncoder().withoutPadding().encodeToString(random),
			Instant.now().plusSeconds(600), userId);
	}

	/** 소셜 서비스에서 확인한 식별자에만 가입하며, 회원·동의·소셜 연결을 함께 저장한다. */
	@Transactional
	public AuthResult signupSocial(SocialAuthorization authorization, String name, String email, String phone,
			String verificationToken, boolean hasPrivacyConsent, boolean hasThirdPartyConsent) {
		requireSocialProfile(authorization, false);
		if (!hasPrivacyConsent || !hasThirdPartyConsent) {
			throw UserException.invalidSignup();
		}
		if (socialAccountRepository.findByProviderAndProviderUserId(authorization.getProvider(),
				authorization.getProviderUserId()).isPresent()) {
			throw UserException.socialAlreadyConnected();
		}
		// 제공받은 프로필을 브라우저에서 다른 이메일이나 이름으로 바꾸지 못하게 한다.
		String signupName = authorization.getName() == null ? name.trim() : authorization.getName();
		String signupEmail = authorization.getEmail() == null ? email.trim() : authorization.getEmail();
		if (userRepository.findByEmail(signupEmail).isPresent()) {
			throw UserException.socialEmailConflict();
		}
		String normalizedPhone = normalizePhone(phone);
		PhoneVerification verification = verifiedPhone(normalizedPhone, verificationToken);
		User user = User.builder().name(signupName).email(signupEmail).phone(normalizedPhone).role(Role.USER).build();
		user.agreeToRequiredTerms();
		user.verifyIdentity(LocalDateTime.ofInstant(verification.getVerifiedAt(), ZoneId.systemDefault()));
		user.recordLogin(LoginProvider.valueOf(authorization.getProvider().name()));
		try {
			userRepository.saveAndFlush(user);
			socialAccountRepository.saveAndFlush(SocialAccount.builder().user(user).provider(authorization.getProvider())
				.providerUserId(authorization.getProviderUserId()).build());
		} catch (DataIntegrityViolationException exception) {
			throw UserException.signupConflict(exception);
		}
		verification.consume(Instant.now());
		return issueTokens(user);
	}

	/** 연결을 시작한 회원과 지금 로그인한 회원이 같을 때만 연결한다. */
	@Transactional
	public void connectSocial(Long userId, SocialAuthorization authorization) {
		requireSocialProfile(authorization, true);
		if (!userId.equals(authorization.getUserId())) {
			throw UserException.socialAuthExpired();
		}
		User user = regularUser(userId);
		if (socialAccountRepository.existsByUserUserIdAndProvider(userId, authorization.getProvider())
				|| socialAccountRepository.findByProviderAndProviderUserId(authorization.getProvider(),
					authorization.getProviderUserId()).isPresent()) {
			throw UserException.socialAlreadyConnected();
		}
		try {
			socialAccountRepository.saveAndFlush(SocialAccount.builder().user(user).provider(authorization.getProvider())
				.providerUserId(authorization.getProviderUserId()).build());
		} catch (DataIntegrityViolationException exception) {
			throw UserException.socialAlreadyConnected();
		}
	}

	public String naverAuthorizationUrl(SocialAuthorization authorization) {
		if (authorization.getProvider() != SocialProvider.NAVER) {
			throw UserException.socialAuthExpired();
		}
		if (!naverProperties.isConfigured()) {
			throw UserException.naverNotConfigured();
		}
		return UriComponentsBuilder.fromUriString("https://nid.naver.com/oauth2.0/authorize")
			.queryParam("response_type", "code").queryParam("client_id", naverProperties.clientId())
			.queryParam("redirect_uri", naverProperties.redirectUri()).queryParam("state", authorization.getState())
			.build().encode().toUriString();
	}

	/** 돌아갈 주소는 설정과 서버에 저장한 목적만 사용하며 외부 입력은 받지 않는다. */
	public String naverReturnUrl(SocialAuthorization authorization, boolean success) {
		String path = authorization != null && authorization.getUserId() != null ? "/mypage" : "/login";
		return naverProperties.frontendUrl().replaceAll("/+$", "") + path + "?naver=" + (success ? "complete" : "error");
	}

	public String kakaoAuthorizationUrl(SocialAuthorization authorization) {
		if (authorization.getProvider() != SocialProvider.KAKAO) {
			throw UserException.socialAuthExpired();
		}
		if (!kakaoProperties.isConfigured()) {
			throw UserException.kakaoNotConfigured();
		}
		return UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
			.queryParam("response_type", "code").queryParam("client_id", kakaoProperties.clientId())
			.queryParam("redirect_uri", kakaoProperties.redirectUri()).queryParam("state", authorization.getState())
			.build().encode().toUriString();
	}

	public String kakaoReturnUrl(SocialAuthorization authorization, boolean success) {
		String path = authorization != null && authorization.getUserId() != null ? "/mypage" : "/login";
		return kakaoProperties.frontendUrl().replaceAll("/+$", "") + path + "?kakao=" + (success ? "complete" : "error");
	}

	@Transactional(readOnly = true)
	public boolean isSocialConnected(Long userId, SocialProvider provider) {
		regularUser(userId);
		return socialAccountRepository.existsByUserUserIdAndProvider(userId, provider);
	}

	/** state와 브라우저 세션을 확인한 뒤 코드를 한 번만 교환한다. 네이버 토큰은 보관하지 않는다. */
	public void authenticateNaver(SocialAuthorization authorization, String state, String code, String error) {
		consumeSocialState(authorization, SocialProvider.NAVER, state);
		if (error != null || code == null || code.isBlank()) {
			throw UserException.naverAuthFailed();
		}
		var parameters = new LinkedMultiValueMap<String, String>();
		parameters.add("grant_type", "authorization_code");
		parameters.add("client_id", naverProperties.clientId());
		parameters.add("client_secret", naverProperties.clientSecret());
		parameters.add("redirect_uri", naverProperties.redirectUri());
		parameters.add("code", code);
		parameters.add("state", state);
		try {
			JsonNode token = naverRestClient.post().uri("https://nid.naver.com/oauth2.0/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(parameters).retrieve().body(JsonNode.class);
			String accessToken = token == null ? null : token.path("access_token").asText(null);
			if (accessToken == null || accessToken.isBlank() || token.has("error")) {
				throw UserException.naverAuthFailed();
			}
			JsonNode response = naverRestClient.get().uri("https://openapi.naver.com/v1/nid/me")
				.headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(JsonNode.class);
			if (response == null || !"00".equals(response.path("resultcode").asText())) {
				throw UserException.naverAuthFailed();
			}
			JsonNode profile = response.path("response");
			String providerUserId = profile.path("id").asText("");
			if (providerUserId.isBlank() || providerUserId.length() > 100) {
				throw UserException.naverAuthFailed();
			}
			authorization.authenticate(providerUserId, profileText(profile, "name", 50), profileText(profile, "email", 255));
		} catch (RestClientException exception) {
			throw UserException.naverAuthFailed();
		}
	}

	/** 카카오 식별자로만 인증하며, 제공받지 못한 가입 정보는 추가 가입 단계에서 받는다. */
	public void authenticateKakao(SocialAuthorization authorization, String state, String code, String error) {
		consumeSocialState(authorization, SocialProvider.KAKAO, state);
		if (error != null || code == null || code.isBlank()) {
			throw UserException.kakaoAuthFailed();
		}
		var parameters = new LinkedMultiValueMap<String, String>();
		parameters.add("grant_type", "authorization_code");
		parameters.add("client_id", kakaoProperties.clientId());
		parameters.add("client_secret", kakaoProperties.clientSecret());
		parameters.add("redirect_uri", kakaoProperties.redirectUri());
		parameters.add("code", code);
		parameters.add("state", state);
		try {
			JsonNode token = kakaoRestClient.post().uri("https://kauth.kakao.com/oauth/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(parameters).retrieve().body(JsonNode.class);
			String accessToken = token == null ? null : token.path("access_token").asText(null);
			if (accessToken == null || accessToken.isBlank() || token.has("error")) {
				throw UserException.kakaoAuthFailed();
			}
			JsonNode profile = kakaoRestClient.get().uri("https://kapi.kakao.com/v2/user/me")
				.headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(JsonNode.class);
			if (profile == null || !profile.path("id").isIntegralNumber() || profile.path("id").asLong() <= 0) {
				throw UserException.kakaoAuthFailed();
			}
			JsonNode account = profile.path("kakao_account");
			String email = account.path("is_email_valid").asBoolean(false)
				&& account.path("is_email_verified").asBoolean(false) ? profileText(account, "email", 255) : null;
			// 닉네임은 실명이 아니므로 이름 입력은 보금의 추가 가입 화면에서 받는다.
			authorization.authenticate(profile.path("id").asText(), profileText(account, "name", 50), email);
		} catch (RestClientException exception) {
			throw UserException.kakaoAuthFailed();
		}
	}

	/** null은 새 사용자이며, 계정은 추가 정보와 필수 동의를 받기 전에는 만들지 않는다. */
	@Transactional
	public AuthResult loginSocial(SocialAuthorization authorization) {
		requireSocialProfile(authorization, false);
		SocialAccount account = socialAccountRepository.findByProviderAndProviderUserId(authorization.getProvider(),
			authorization.getProviderUserId()).orElse(null);
		if (account == null) {
			if (authorization.getEmail() != null && userRepository.findByEmail(authorization.getEmail()).isPresent()) {
				throw UserException.socialEmailConflict();
			}
			return null;
		}
		User user = regularUser(account.getUser().getUserId());
		user.recordLogin(LoginProvider.valueOf(authorization.getProvider().name()));
		return issueTokens(user);
	}

	private User regularUser(Long userId) {
		User user = findUser(userId);
		if (user.getRole() != Role.USER) {
			throw UserException.socialRoleNotAllowed();
		}
		return user;
	}

	private void requireSocialProfile(SocialAuthorization authorization, boolean isLink) {
		if (authorization == null || !Instant.now().isBefore(authorization.getExpiresAt())
				|| authorization.getProviderUserId() == null || (authorization.getUserId() != null) != isLink) {
			throw UserException.socialAuthExpired();
		}
	}

	private void consumeSocialState(SocialAuthorization authorization, SocialProvider provider, String state) {
		if (authorization == null || authorization.getProvider() != provider
				|| !Instant.now().isBefore(authorization.getExpiresAt())
				|| authorization.getState() == null || state == null
				|| !MessageDigest.isEqual(authorization.getState().getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
			throw UserException.socialAuthExpired();
		}
		authorization.consumeState();
	}

	private String profileText(JsonNode profile, String field, int maxLength) {
		String value = profile.path(field).asText("").trim();
		return value.isEmpty() || value.length() > maxLength ? null : value;
	}

	private PhoneVerification verifiedPhone(String phone, String verificationToken) {
		if (verificationToken == null || verificationToken.isBlank()) {
			throw UserException.phoneVerificationRequired();
		}
		PhoneVerification verification = phoneVerificationRepository.findByVerificationToken(verificationToken)
			.orElseThrow(UserException::phoneVerificationRequired);
		if (!verification.getPhone().equals(phone) || verification.getVerifiedAt() == null
				|| verification.getConsumedAt() != null || !Instant.now().isBefore(verification.getTokenExpiresAt())) {
			throw UserException.phoneVerificationRequired();
		}
		return verification;
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
		user.recordLogin(LoginProvider.LOCAL);
		return issueTokens(user);
	}

	private AuthResult issueTokens(User user) {
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
