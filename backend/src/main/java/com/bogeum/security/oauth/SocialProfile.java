package com.bogeum.security.oauth;

import java.io.Serializable;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import com.bogeum.security.EmailNormalizer;

/** 8장 교재의 카카오 속성 추출을 네이버·구글까지 확장한다. 이메일은 가입 입력용 참고값이다. */
public record SocialProfile(String provider, String providerUserId, String email) implements Serializable {
	public static SocialProfile from(String provider, Map<String, Object> attributes) {
		Map<?, ?> source = attributes;
		String id;
		Object email;
		switch (provider) {
			case "google" -> {
				id = value(attributes.get("sub"));
				email = attributes.get("email");
			}
			case "kakao" -> {
				id = value(attributes.get("id"));
				source = attributes.get("kakao_account") instanceof Map<?, ?> account ? account : Map.of();
				email = source.get("email");
			}
			case "naver" -> {
				source = attributes.get("response") instanceof Map<?, ?> response ? response : Map.of();
				id = value(source.get("id"));
				email = source.get("email");
			}
			default -> throw new OAuth2AuthenticationException("unsupported_provider");
		}
		if (id == null || id.isBlank() || id.length() > 255) {
			throw new OAuth2AuthenticationException("invalid_user_info");
		}
		return new SocialProfile(provider, id, EmailNormalizer.normalize(value(email)));
	}

	private static String value(Object value) {
		return value == null ? null : value.toString();
	}
}
