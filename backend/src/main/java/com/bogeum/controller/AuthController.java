package com.bogeum.controller;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bogeum.dto.MemberResponseDTO;
import com.bogeum.security.RecentLoginService;
import com.bogeum.security.RecentLoginService.RecentLoginResponse;
import com.bogeum.security.dto.MemberSecurityDTO;
import com.bogeum.service.MemberService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

/**
 * CSRF 토큰 발급과 현재 로그인 회원 조회를 담당한다.
 * 로그인 POST는 JsonLoginFilter, 로그아웃 POST는 SecurityConfig의 로그아웃 필터에서 처리한다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final MemberService memberService;
	private final RecentLoginService recentLoginService;

	@GetMapping("/recent-login")
	public RecentLoginResponse recentLogin(HttpServletRequest request, HttpServletResponse response) {
		response.setHeader("Cache-Control", "no-store");
		return recentLoginService.read(request);
	}

	@GetMapping("/csrf")
	public Map<String, String> csrf(CsrfToken csrfToken) {
		// 상태를 변경하는 요청에는 여기서 받은 headerName/token을 헤더에 넣고 같은 세션 쿠키를 보낸다.
		return Map.of("headerName", csrfToken.getHeaderName(), "token", csrfToken.getToken());
	}

	@GetMapping("/me")
	public MemberResponseDTO me(@AuthenticationPrincipal MemberSecurityDTO principal) {
		// 요청 파라미터가 아닌 세션의 인증 주체에서 ID를 얻고, DB의 최신 계정 상태를 다시 확인한다.
		return memberService.getCurrentMember(principal.getMemberId());
	}
}
