package com.bogeum.config;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.bogeum.common.exception.ErrorCode;
import com.bogeum.config.handler.CustomAuthenticationEntryPoint;
import com.bogeum.domain.user.UserException;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import com.bogeum.utils.JwtUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** 공개 경로를 제외하고 JWT를 검사해 SecurityContext에 인증 정보를 등록한다. */
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

	private final UserService userService;
	private final JwtUtil jwtUtil;
	private final CustomAuthenticationEntryPoint authenticationEntryPoint;
	private final RequestMatcher excludedRequests;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return excludedRequests.matches(request);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header == null) {
			chain.doFilter(request, response); // 공개/보호 경로 판단은 Security 설정이 한다.
			return;
		}
		if (!header.startsWith("Bearer ") || header.length() <= 7) {
			authenticationEntryPoint.writeError(response, ErrorCode.ACCESS_TOKEN_INVALID);
			return;
		}
		try {
			var claims = jwtUtil.validateAccessToken(header.substring(7));
			Long userId = Long.valueOf(claims.getSubject());
			var user = userService.findUser(userId);
			LoginUser principal = new LoginUser(user);
			principal.eraseCredentials();
			var authentication = UsernamePasswordAuthenticationToken.authenticated(
				principal, null, principal.getAuthorities());
			var context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);
		} catch (UserException exception) {
			SecurityContextHolder.clearContext();
			authenticationEntryPoint.writeError(response, exception.getErrorCode());
			return;
		}
		// 컨트롤러의 예외까지 토큰 오류로 바꾸지 않도록 try 범위 바깥에서 호출한다.
		chain.doFilter(request, response);
	}
}
