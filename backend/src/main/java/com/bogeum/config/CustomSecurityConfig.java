package com.bogeum.config;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.bogeum.config.handler.CustomAccessDeniedHandler;
import com.bogeum.config.handler.CustomAuthenticationEntryPoint;
import com.bogeum.domain.user.UserService;
import com.bogeum.utils.JwtUtil;

/** 요청별 접근 권한, CSRF, 인증 오류 응답과 보안 필터 순서를 설정한다. */
@Configuration
@EnableMethodSecurity
@EnableJpaRepositories(basePackages = "com.bogeum.domain", considerNestedRepositories = true)
public class CustomSecurityConfig {
	private static final Set<String> PUBLIC_PATHS = Set.of(
		"/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
		"/api/v1/auth/csrf", "/api/v1/auth/signup/user", "/api/health");

	/** 사용자 조회와 비밀번호 검증을 Spring Security 인증 관리자에 연결한다. */
	@Bean
	public AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
		var provider = new DaoAuthenticationProvider(users);
		provider.setPasswordEncoder(encoder);
		return new ProviderManager(provider);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, UserService userService, JwtUtil jwtUtil,
			CustomAuthenticationEntryPoint authenticationEntryPoint, CustomAccessDeniedHandler accessDeniedHandler,
			@Value("${auth.cookie-secure:true}") boolean cookieSecure) throws Exception {
		var csrfRepository = new CookieCsrfTokenRepository();
		csrfRepository.setCookieName("BOGEUM_CSRF");
		csrfRepository.setCookieCustomizer(cookie -> cookie.path("/").httpOnly(true)
			.secure(cookieSecure).sameSite("Lax"));
		var publicRequests = new OrRequestMatcher(PUBLIC_PATHS.stream()
			.map(path -> PathPatternRequestMatcher.withDefaults().matcher(path)).toArray(RequestMatcher[]::new));

		http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// HttpOnly Refresh 쿠키를 사용하므로 CSRF를 끄지 않는다.
			// 기본 XOR 핸들러 유지: GET /csrf에서 받은 마스킹된 값을 헤더로 보낸다.
			.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(requests -> requests
				.requestMatchers(publicRequests).permitAll()
				.requestMatchers("/error").permitAll()
				.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
				.anyRequest().authenticated())
			.exceptionHandling(errors -> errors
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			// Bean/Component로 이 필터를 등록하지 않아 서블릿 필터의 중복 실행을 막는다.
			.addFilterBefore(new JwtFilter(userService, jwtUtil, authenticationEntryPoint,
				publicRequests), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
