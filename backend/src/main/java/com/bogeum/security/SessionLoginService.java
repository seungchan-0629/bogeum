package com.bogeum.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import com.bogeum.security.dto.MemberSecurityDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** 추가 가입/연결 완료 시 임시 권한을 정식 회원 세션으로 교체한다. */
@Component
@RequiredArgsConstructor
public class SessionLoginService {
	private final SecurityContextRepository contextRepository;
	private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

	public void login(MemberSecurityDTO principal, HttpServletRequest request, HttpServletResponse response) {
		principal.eraseCredentials();
		var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
		sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
		var context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
	}
}
