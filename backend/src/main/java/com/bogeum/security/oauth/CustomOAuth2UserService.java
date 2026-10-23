package com.bogeum.security.oauth;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import com.bogeum.exception.ApiException;
import com.bogeum.exception.ErrorCode;
import com.bogeum.service.SocialAccountService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 제공자 API 호출과 토큰 검증 흐름은 Spring Security에 맡기고 회원 조회만 확장한다. */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
	private final SocialAccountService socialAccountService;
	private final HttpServletRequest servletRequest;

	@Override
	public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
		OAuth2User user;
		try {
			user = super.loadUser(request);
		} catch (IllegalArgumentException exception) {
			// 제공자가 필수 사용자 ID 속성을 누락한 경우도 인증 실패 경로로 처리한다.
			throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"), exception);
		}
		SocialProfile profile = SocialProfile.from(request.getClientRegistration().getRegistrationId(),
			user.getAttributes());
		if (servletRequest.getAttribute(SocialAuthorizationRequestRepository.LINK_INTENT)
				instanceof SocialAuthorizationRequestRepository.LinkIntent intent) {
			intent.requireValid();
			if (!intent.provider().equals(profile.provider())) {
				throw new OAuth2AuthenticationException(new OAuth2Error("invalid_link_provider"));
			}
			try {
				// 일반 로그인 회원 조회보다 먼저 연결을 처리해 다른 회원의 소셜 계정으로 전환하지 않는다.
				return socialAccountService.link(new PendingSocialUser(profile), intent.memberId());
			} catch (ApiException exception) {
				throw new OAuth2AuthenticationException(new OAuth2Error(exception.getErrorCode().name()), exception);
			} catch (DataIntegrityViolationException exception) {
				throw new OAuth2AuthenticationException(new OAuth2Error(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED.name()), exception);
			}
		}
		return socialAccountService.loadUser(profile);
	}
}
