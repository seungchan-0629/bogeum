package com.bogeum.security.handler;

import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import com.bogeum.security.dto.MemberSecurityDTO;
import com.bogeum.security.RecentLoginService;
import com.bogeum.security.RecentLoginService.LoginType;
import com.bogeum.security.oauth.SocialAuthorizationRequestRepository;
import com.bogeum.security.oauth.PendingSocialUser;
import com.bogeum.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
	private final JsonResponseWriter responseWriter;
	private final MemberService memberService;
	private final RecentLoginService recentLoginService;

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		if (authentication.getPrincipal() instanceof PendingSocialUser pending) {
			responseWriter.write(response, 200, new PendingResponse("REGISTRATION_REQUIRED",
				pending.profile().provider(), pending.profile().email(), pending.expiresAt()));
		} else {
			MemberSecurityDTO principal = (MemberSecurityDTO) authentication.getPrincipal();
			var member = memberService.getCurrentMember(principal.getMemberId());
			// 로그인한 회원의 계정 연결은 새로운 로그인이 아니므로 기존 안내를 유지한다.
			if (request.getAttribute(SocialAuthorizationRequestRepository.LINK_INTENT) == null) {
				String provider = ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId();
				recentLoginService.remember(LoginType.fromProvider(provider), request, response);
			}
			responseWriter.write(response, 200, member);
		}
	}

	public record PendingResponse(String status, String provider, String email, java.time.Instant expiresAt) { }
}
