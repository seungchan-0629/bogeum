package com.bogeum.security;

import org.springframework.stereotype.Component;
import com.bogeum.config.SocialClientRegistrations;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** 브라우저별 안내용 기록이다. 회원 식별이나 인증/인가 판단에 사용하지 않는다. */
@Component
@RequiredArgsConstructor
public class RecentLoginService {

	public static final String COOKIE_NAME = "BOGEUM_RECENT_LOGIN";
	private static final int MAX_AGE_SECONDS = 30 * 24 * 60 * 60;
	private final SocialClientRegistrations registrations;

	public void remember(LoginType type, HttpServletRequest request, HttpServletResponse response) {
		Cookie cookie = new Cookie(COOKIE_NAME, type.name());
		cookie.setPath(request.getContextPath() + "/api");
		cookie.setMaxAge(MAX_AGE_SECONDS);
		cookie.setHttpOnly(true);
		cookie.setSecure(request.isSecure());
		cookie.setAttribute("SameSite", "Lax");
		response.addCookie(cookie);
	}

	public RecentLoginResponse read(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (!COOKIE_NAME.equals(cookie.getName())) continue;
				try {
					LoginType type = LoginType.valueOf(cookie.getValue());
					boolean available = type == LoginType.NORMAL
						|| registrations.findByRegistrationId(type.provider) != null;
					return new RecentLoginResponse(type, type.label,
						"이 브라우저에서 최근 " + type.label + "로 로그인했어요.", available);
				} catch (IllegalArgumentException | NullPointerException ignored) {
					// 오래되거나 변조된 안내 쿠키는 로그인 화면 진입을 방해하지 않는다.
					return RecentLoginResponse.empty();
				}
			}
		}
		return RecentLoginResponse.empty();
	}

	public enum LoginType {
		NORMAL(null, "이메일"), KAKAO("kakao", "카카오"), NAVER("naver", "네이버"), GOOGLE("google", "구글");

		private final String provider;
		private final String label;

		LoginType(String provider, String label) {
			this.provider = provider;
			this.label = label;
		}

		public static LoginType fromProvider(String provider) {
			for (LoginType type : values()) {
				if (type.provider != null && type.provider.equals(provider)) return type;
			}
			throw new IllegalArgumentException("Unsupported login provider");
		}
	}

	public record RecentLoginResponse(LoginType loginType, String label, String message, boolean available) {
		private static RecentLoginResponse empty() {
			return new RecentLoginResponse(null, null, null, false);
		}
	}
}
