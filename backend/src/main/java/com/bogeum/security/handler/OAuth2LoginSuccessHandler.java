package com.bogeum.security.handler;

import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import com.bogeum.security.dto.MemberSecurityDTO;
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

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		if (authentication.getPrincipal() instanceof PendingSocialUser pending) {
			responseWriter.write(response, 200, new PendingResponse("REGISTRATION_REQUIRED",
				pending.profile().provider(), pending.profile().email(), pending.expiresAt()));
		} else {
			MemberSecurityDTO principal = (MemberSecurityDTO) authentication.getPrincipal();
			responseWriter.write(response, 200, memberService.getCurrentMember(principal.getMemberId()));
		}
	}

	public record PendingResponse(String status, String provider, String email, java.time.Instant expiresAt) { }
}
