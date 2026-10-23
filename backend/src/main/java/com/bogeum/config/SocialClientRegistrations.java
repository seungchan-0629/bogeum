package com.bogeum.config;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

/** 키를 설정한 제공자만 등록한다. 키가 없어도 일반 로그인은 정상적으로 시작된다. */
@Component
public class SocialClientRegistrations implements ClientRegistrationRepository, Iterable<ClientRegistration> {
	private final List<ClientRegistration> registrations = new ArrayList<>();

	public SocialClientRegistrations(Environment environment) {
		for (String provider : List.of("kakao", "naver", "google")) {
			String prefix = "bogeum.oauth2." + provider + ".";
			String clientId = environment.getProperty(prefix + "client-id", "");
			String secret = environment.getProperty(prefix + "client-secret", "");
			if (clientId.isBlank() && secret.isBlank()) continue;
			if (clientId.isBlank() || secret.isBlank()) {
				throw new IllegalStateException(provider + " OAuth2 requires both client-id and client-secret");
			}
			ClientRegistration.Builder builder;
			if (provider.equals("google")) {
				// 교재의 OAuth2UserService 경로를 사용한다. openid를 추가할 때는 OidcUserService도 구현해야 한다.
				builder = CommonOAuth2Provider.GOOGLE.getBuilder(provider).scope("profile", "email");
			} else {
				builder = ClientRegistration.withRegistrationId(provider)
					.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
					.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
				if (provider.equals("kakao")) {
					builder.clientName("카카오")
						.authorizationUri("https://kauth.kakao.com/oauth/authorize")
						.tokenUri("https://kauth.kakao.com/oauth/token")
						.userInfoUri("https://kapi.kakao.com/v2/user/me")
						.userNameAttributeName("id");
				} else {
					builder.clientName("네이버")
						.authorizationUri("https://nid.naver.com/oauth2.0/authorize")
						.tokenUri("https://nid.naver.com/oauth2.0/token")
						.userInfoUri("https://openapi.naver.com/v1/nid/me")
						.userNameAttributeName("response");
				}
			}
			registrations.add(builder.clientId(clientId).clientSecret(secret)
				.redirectUri(environment.getProperty(prefix + "redirect-uri",
					"{baseUrl}/api/login/oauth2/code/{registrationId}"))
				.build());
		}
	}

	@Override
	public ClientRegistration findByRegistrationId(String registrationId) {
		return registrations.stream().filter(item -> item.getRegistrationId().equals(registrationId))
			.findFirst().orElse(null);
	}

	public boolean isEmpty() {
		return registrations.isEmpty();
	}

	@Override
	public Iterator<ClientRegistration> iterator() {
		return List.copyOf(registrations).iterator();
	}
}
