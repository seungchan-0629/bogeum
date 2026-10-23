package com.bogeum.security.handler;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.bogeum.security.dto.MemberSecurityDTO;
import com.bogeum.security.RecentLoginService;
import com.bogeum.security.RecentLoginService.LoginType;
import com.bogeum.service.MemberService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 세션 인증 처리가 끝난 뒤 최신 회원 정보를 조회해 로그인 성공 응답으로 반환한다.
 */
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final MemberService memberService;
	private final JsonResponseWriter responseWriter;
	private final RecentLoginService recentLoginService;

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		MemberSecurityDTO principal = (MemberSecurityDTO) authentication.getPrincipal();
		var member = memberService.getCurrentMember(principal.getMemberId());
		recentLoginService.remember(LoginType.NORMAL, request, response);
		responseWriter.write(response, HttpServletResponse.SC_OK, member);
	}
}
