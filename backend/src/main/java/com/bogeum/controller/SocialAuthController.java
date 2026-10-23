package com.bogeum.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.bogeum.config.SocialClientRegistrations;
import com.bogeum.dto.LoginRequestDTO;
import com.bogeum.dto.MemberResponseDTO;
import com.bogeum.dto.SocialJoinDTO;
import com.bogeum.dto.SocialLinkRequestDTO;
import com.bogeum.dto.SocialAccountResponseDTO;
import com.bogeum.exception.ApiException;
import com.bogeum.exception.ErrorCode;
import com.bogeum.security.SessionLoginService;
import com.bogeum.security.PasswordPolicy;
import com.bogeum.security.dto.MemberSecurityDTO;
import com.bogeum.security.handler.OAuth2LoginSuccessHandler.PendingResponse;
import com.bogeum.security.oauth.PendingSocialUser;
import com.bogeum.security.oauth.SocialAuthorizationRequestRepository;
import com.bogeum.security.oauth.SocialAuthorizationRequestRepository.LinkStartResponse;
import com.bogeum.service.MemberService;
import com.bogeum.service.SocialAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SocialAuthController {
	private final SocialClientRegistrations registrations;
	private final SocialAccountService socialAccountService;
	private final MemberService memberService;
	private final AuthenticationManager authenticationManager;
	private final SessionLoginService sessionLoginService;
	private final SocialAuthorizationRequestRepository authorizationRequests;

	@GetMapping("/auth/social/providers")
	public List<Map<String, String>> providers() {
		List<Map<String, String>> result = new ArrayList<>();
		registrations.forEach(item -> result.add(Map.of("provider", item.getRegistrationId(),
			"loginUrl", "/api/oauth2/authorization/" + item.getRegistrationId())));
		return result;
	}

	@GetMapping("/oauth2/authorization/{provider}")
	public void unavailable() {
		// 등록된 제공자는 컨트롤러 전에 OAuth2 리다이렉트 필터에서 처리한다.
		throw new ApiException(ErrorCode.SOCIAL_LOGIN_UNAVAILABLE);
	}

	@GetMapping("/auth/social/pending")
	public PendingResponse pending(@AuthenticationPrincipal PendingSocialUser pending) {
		pending.requireValid();
		return new PendingResponse("REGISTRATION_REQUIRED", pending.profile().provider(),
			pending.profile().email(), pending.expiresAt());
	}

	@PostMapping("/auth/social/signup")
	@ResponseStatus(org.springframework.http.HttpStatus.CREATED)
	public MemberResponseDTO signup(@AuthenticationPrincipal PendingSocialUser pending,
			@Valid @RequestBody SocialJoinDTO body, HttpServletRequest request, HttpServletResponse response) {
		MemberSecurityDTO principal = socialAccountService.register(pending, body);
		sessionLoginService.login(principal, request, response);
		return memberService.getCurrentMember(principal.getMemberId());
	}

	@PostMapping("/auth/social/link")
	public MemberResponseDTO link(@AuthenticationPrincipal PendingSocialUser pending,
			@Valid @RequestBody LoginRequestDTO body, HttpServletRequest request, HttpServletResponse response) {
		pending.requireValid();
		// 소셜 인증과 기존 계정 비밀번호 재인증을 둘 다 통과해야 명시적으로 연결한다.
		MemberSecurityDTO target = verifyPassword(body.getEmail(), body.getPassword());
		MemberSecurityDTO principal = socialAccountService.link(pending, target.getMemberId());
		sessionLoginService.login(principal, request, response);
		return memberService.getCurrentMember(principal.getMemberId());
	}

	@GetMapping("/auth/social/accounts")
	public List<SocialAccountResponseDTO> accounts(@AuthenticationPrincipal MemberSecurityDTO principal) {
		return socialAccountService.linkedAccounts(principal.getMemberId());
	}

	@PostMapping("/auth/social/link/{provider}")
	public LinkStartResponse startLink(@AuthenticationPrincipal MemberSecurityDTO principal,
			@PathVariable String provider, @Valid @RequestBody SocialLinkRequestDTO body,
			HttpServletRequest request, HttpServletResponse response) {
		MemberSecurityDTO verified = verifyPassword(principal.getUsername(), body.password());
		socialAccountService.requireLinkAvailable(verified.getMemberId(), provider);
		return authorizationRequests.startLink(provider, verified.getMemberId(), request, response);
	}

	private MemberSecurityDTO verifyPassword(String email, String password) {
		if (PasswordPolicy.exceedsByteLimit(password)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED,
				Map.of("password", "비밀번호는 72바이트 이하여야 합니다."));
		}
		try {
			var verified = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(email, password));
			return (MemberSecurityDTO) verified.getPrincipal();
		} catch (AuthenticationException exception) {
			throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
		}
	}
}
