package com.bogeum.domain.user;

import com.bogeum.domain.agent.AgentService;
import com.bogeum.domain.agent.model.Agent;
import com.bogeum.domain.user.model.User;
import com.bogeum.domain.user.model.SocialAuthorization;
import com.bogeum.common.enums.Role;
import com.bogeum.common.enums.SocialProvider;

import com.bogeum.domain.user.model.UserDto;
import com.bogeum.domain.user.model.UserDto.CsrfResponse;
import com.bogeum.domain.user.model.UserDto.LoginReq;
import com.bogeum.domain.user.model.UserDto.LoginRes;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import com.bogeum.domain.user.model.UserDto.TokenResponse;
import com.bogeum.domain.user.model.UserDto.RefreshRequest;
import com.bogeum.domain.user.model.UserDto.AuthResult;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {
	private static final String SOCIAL_AUTHORIZATION = "socialAuthorization";
	private final UserService userService;
	private final AgentService agentService;

	/** 일반 회원가입 정보를 검증하고 생성된 사용자 식별자를 반환한다. */
	@PostMapping("/signup/user")
	@ResponseStatus(HttpStatus.CREATED)
	public UserDto.SignupResponse signup(@Valid @RequestBody UserDto.SignupRequest request) {
		return UserDto.SignupResponse.from(userService.signup(
			request.getEmail(), request.getPassword(), request.getName(), request.getPhone(),
			request.getAgreements().getPrivacyCollect(), request.getAgreements().getThirdPartyProvide(),
			request.getVerificationToken()));
	}

	/** 가입 전에 휴대폰 인증번호를 발송한다. */
	@PostMapping("/identity-verification")
	public UserDto.IdentityVerificationRes requestIdentityVerification(
			@Valid @RequestBody UserDto.IdentityVerificationReq request) {
		return UserDto.IdentityVerificationRes.from(userService.requestIdentityVerification(request.getPhone()));
	}

	/** 인증번호를 확인하고 회원가입용 토큰을 발급한다. */
	@PostMapping("/identity-verification/confirm")
	public UserDto.IdentityVerificationConfirmRes confirmIdentityVerification(
			@Valid @RequestBody UserDto.IdentityVerificationConfirmReq request) {
		return UserDto.IdentityVerificationConfirmRes.from(
			userService.confirmIdentityVerification(request.getVerificationId(), request.getCode()));
	}


	/** C: 기존 보금과 같은 JSON {email,password}를 받는다. */
	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginReq request) {
		return tokenResponse(userService.login(request.getEmail(), request.getPassword()));
	}

	@PostMapping("/social/{provider:naver|kakao}/authorize")
	public UserDto.SocialAuthorizationResponse authorizeSocial(@PathVariable String provider, HttpServletRequest request) {
		return startSocial(socialProvider(provider), request, null);
	}

	@PostMapping("/social/{provider:naver|kakao}/link/authorize")
	public UserDto.SocialAuthorizationResponse authorizeSocialLink(@PathVariable String provider, @AuthenticationPrincipal LoginUser loginUser,
			HttpServletRequest request) {
		return startSocial(socialProvider(provider), request, loginUser.getUserId());
	}

	@PostMapping("/social/{provider:naver|kakao}/signup")
	public TokenResponse signupSocial(@PathVariable String provider, @Valid @RequestBody UserDto.SocialSignupRequest body,
			HttpServletRequest request) {
		HttpSession session = socialSession(request);
		synchronized (session) {
			AuthResult result = userService.signupSocial(socialAuthorization(session, socialProvider(provider)), body.getName(), body.getEmail(),
				body.getPhone(), body.getVerificationToken(), body.getAgreements().getPrivacyCollect(),
				body.getAgreements().getThirdPartyProvide());
			session.removeAttribute(SOCIAL_AUTHORIZATION);
			return TokenResponse.from(result);
		}
	}

	@PostMapping("/social/{provider:naver|kakao}/login")
	public UserDto.SocialLoginResponse loginSocial(@PathVariable String provider, HttpServletRequest request) {
		HttpSession session = socialSession(request);
		synchronized (session) {
			SocialAuthorization authorization = socialAuthorization(session, socialProvider(provider));
			AuthResult result = userService.loginSocial(authorization);
			if (result == null) {
				return new UserDto.SocialLoginResponse(true, authorization.getName(), authorization.getEmail(), null);
			}
			session.removeAttribute(SOCIAL_AUTHORIZATION);
			return new UserDto.SocialLoginResponse(false, null, null, TokenResponse.from(result));
		}
	}

	@PostMapping("/social/{provider:naver|kakao}/link")
	public UserDto.SocialConnectionResponse connectSocial(@PathVariable String provider, @AuthenticationPrincipal LoginUser loginUser,
			HttpServletRequest request) {
		HttpSession session = socialSession(request);
		synchronized (session) {
			userService.connectSocial(loginUser.getUserId(), socialAuthorization(session, socialProvider(provider)));
			session.removeAttribute(SOCIAL_AUTHORIZATION);
			return new UserDto.SocialConnectionResponse(true);
		}
	}

	/** 외부 인증 콜백만 JSON 대신 고정된 보금 화면으로 돌려보낸다. */
	@GetMapping("/social/{provider:naver|kakao}/callback")
	public ResponseEntity<Void> socialCallback(@PathVariable String provider, @RequestParam(required = false) String state,
			@RequestParam(required = false) String code, @RequestParam(required = false) String error,
			HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		SocialProvider socialProvider = socialProvider(provider);
		SocialAuthorization authorization = null;
		boolean success = false;
		if (session != null) {
			synchronized (session) {
				try {
					authorization = socialAuthorization(session, socialProvider);
					if (socialProvider == SocialProvider.NAVER) {
						userService.authenticateNaver(authorization, state, code, error);
					} else {
						userService.authenticateKakao(authorization, state, code, error);
					}
					success = true;
				} catch (UserException exception) {
					session.removeAttribute(SOCIAL_AUTHORIZATION);
				}
			}
		}
		return ResponseEntity.status(HttpStatus.FOUND)
			.header("Location", socialProvider == SocialProvider.NAVER
				? userService.naverReturnUrl(authorization, success) : userService.kakaoReturnUrl(authorization, success))
			.header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer").build();
	}

	@GetMapping("/social/{provider:naver|kakao}/connection")
	public UserDto.SocialConnectionResponse socialConnection(@PathVariable String provider, @AuthenticationPrincipal LoginUser loginUser) {
		return new UserDto.SocialConnectionResponse(userService.isSocialConnected(loginUser.getUserId(), socialProvider(provider)));
	}

	/** R: 요청 파라미터 userId 대신 검증된 principal에서 회원 번호를 꺼낸다. */
	@GetMapping("/me")
	public LoginRes me(@AuthenticationPrincipal LoginUser loginUser) {
		var user = userService.findUser(loginUser.getUserId());
		return LoginRes.from(user, agentFor(user));
	}

	/** 마스킹된 CSRF 값은 프론트의 메모리에 보관한다. 쿠키 원문은 JS로 읽지 않는다. */
	@GetMapping("/csrf")
	public CsrfResponse csrf(CsrfToken token) {
		return new CsrfResponse(token.getHeaderName(), token.getToken());
	}

	/** U: JSON으로 받은 Refresh Token과 CSRF 헤더로 갱신한다. */
	@PostMapping("/refresh")
	public TokenResponse refresh(@RequestBody(required = false) RefreshRequest request) {
		return tokenResponse(userService.refresh(request == null ? null : request.getRefreshToken()));
	}

	/** 로그아웃 성공을 응답한다. 브라우저의 토큰 삭제는 프론트에서 처리한다. */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout()
	{
	}
	private Agent agentFor(User user) {
		return user.getRole() == Role.AGENT
			? agentService.findByUserId(user.getUserId()) : null;
	}

	private TokenResponse tokenResponse(AuthResult result) {
		return TokenResponse.from(result, agentFor(result.getUser()));
	}

	private SocialProvider socialProvider(String provider) {
		return SocialProvider.valueOf(provider.toUpperCase(Locale.ROOT));
	}

	private UserDto.SocialAuthorizationResponse startSocial(SocialProvider provider, HttpServletRequest request, Long userId) {
		SocialAuthorization authorization = userService.createSocialAuthorization(provider, userId);
		String authorizationUrl = provider == SocialProvider.NAVER
			? userService.naverAuthorizationUrl(authorization) : userService.kakaoAuthorizationUrl(authorization);
		HttpSession session = request.getSession();
		request.changeSessionId();
		session.setMaxInactiveInterval(600);
		synchronized (session) {
			session.setAttribute(SOCIAL_AUTHORIZATION, authorization);
		}
		return new UserDto.SocialAuthorizationResponse(authorizationUrl);
	}

	private HttpSession socialSession(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			throw UserException.socialAuthExpired();
		}
		return session;
	}

	private SocialAuthorization socialAuthorization(HttpSession session, SocialProvider provider) {
		if (session.getAttribute(SOCIAL_AUTHORIZATION) instanceof SocialAuthorization authorization
				&& authorization.getProvider() == provider) {
			return authorization;
		}
		throw UserException.socialAuthExpired();
	}

}
