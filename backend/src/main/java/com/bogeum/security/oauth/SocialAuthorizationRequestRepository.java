package com.bogeum.security.oauth;

import java.io.Serializable;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import com.bogeum.exception.ApiException;
import com.bogeum.exception.ErrorCode;
import com.bogeum.security.dto.MemberSecurityDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 연결 의도를 OAuth state와 함께 서버 세션에 보관하고 콜백에서 한 번만 소비한다. */
@Component
public class SocialAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
	public static final String LINK_INTENT = SocialAuthorizationRequestRepository.class.getName() + ".LINK";
	public static final String ORIGINAL_AUTHENTICATION = SocialAuthorizationRequestRepository.class.getName() + ".AUTH";
	private final HttpSessionOAuth2AuthorizationRequestRepository delegate = new HttpSessionOAuth2AuthorizationRequestRepository();
	private final ClientRegistrationRepository registrations;
	private final DefaultOAuth2AuthorizationRequestResolver resolver;

	public SocialAuthorizationRequestRepository(ClientRegistrationRepository registrations) {
		this.registrations = registrations;
		this.resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/api/oauth2/authorization");
	}

	public LinkStartResponse startLink(String provider, Long memberId,
			HttpServletRequest request, HttpServletResponse response) {
		if (registrations.findByRegistrationId(provider) == null) {
			throw new ApiException(ErrorCode.SOCIAL_LOGIN_UNAVAILABLE);
		}
		LinkIntent intent = new LinkIntent(memberId, provider, Instant.now().plusSeconds(600));
		OAuth2AuthorizationRequest authorization = OAuth2AuthorizationRequest.from(resolver.resolve(request, provider))
			.attributes(attributes -> attributes.put(LINK_INTENT, intent)).build();
		saveAuthorizationRequest(authorization, request, response);
		return new LinkStartResponse(authorization.getAuthorizationRequestUri(), intent.expiresAt());
	}

	@Override
	public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
		return delegate.loadAuthorizationRequest(request);
	}

	@Override
	public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorization,
			HttpServletRequest request, HttpServletResponse response) {
		delegate.saveAuthorizationRequest(authorization, request, response);
	}

	@Override
	public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
		Authentication current = SecurityContextHolder.getContext().getAuthentication();
		if (current != null && current.isAuthenticated() && current.getPrincipal() instanceof MemberSecurityDTO) {
			request.setAttribute(ORIGINAL_AUTHENTICATION, current);
		}
		// delegate가 세션과 state 일치를 확인한 요청만 반환한다.
		OAuth2AuthorizationRequest authorization = delegate.removeAuthorizationRequest(request, response);
		if (authorization != null && authorization.getAttribute(LINK_INTENT) instanceof LinkIntent intent) {
			intent.requireValid();
			if (current == null || !current.isAuthenticated()
					|| !(current.getPrincipal() instanceof MemberSecurityDTO member)
					|| !member.getMemberId().equals(intent.memberId())) {
				throw new OAuth2AuthenticationException(new OAuth2Error("invalid_link_session"));
			}
			request.setAttribute(LINK_INTENT, intent);
		}
		return authorization;
	}

	public record LinkStartResponse(String authorizationUrl, Instant expiresAt) { }

	public record LinkIntent(Long memberId, String provider, Instant expiresAt) implements Serializable {
		public void requireValid() {
			if (!Instant.now().isBefore(expiresAt)) {
				throw new OAuth2AuthenticationException(new OAuth2Error(ErrorCode.SOCIAL_LINK_EXPIRED.name()));
			}
		}
	}
}
