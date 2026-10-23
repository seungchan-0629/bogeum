package com.bogeum.security.oauth;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import jakarta.servlet.http.HttpServletRequest;

/** 미설정/지원하지 않는 제공자는 컨트롤러의 공통 503 응답으로 보낸다. */
public class SocialAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
	private static final String BASE_URI = "/api/oauth2/authorization";
	private final ClientRegistrationRepository registrations;
	private final DefaultOAuth2AuthorizationRequestResolver delegate;

	public SocialAuthorizationRequestResolver(ClientRegistrationRepository registrations) {
		this.registrations = registrations;
		this.delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations, BASE_URI);
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		if (path.startsWith(BASE_URI + "/")
				&& registrations.findByRegistrationId(path.substring(BASE_URI.length() + 1)) == null) {
			return null;
		}
		return delegate.resolve(request);
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
		return registrations.findByRegistrationId(registrationId) == null ? null : delegate.resolve(request, registrationId);
	}
}
