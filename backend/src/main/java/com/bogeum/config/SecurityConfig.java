package com.bogeum.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import com.bogeum.exception.ErrorCode;
import com.bogeum.security.CustomUserDetailsService;
import com.bogeum.security.filter.JsonLoginFilter;
import com.bogeum.security.handler.JsonResponseWriter;
import com.bogeum.security.handler.LoginSuccessHandler;

import jakarta.validation.Validator;
import jakarta.servlet.DispatcherType;
import tools.jackson.databind.ObjectMapper;

/**
 * 컨트롤러보다 먼저 실행되는 보안 필터 체인을 구성한다.
 * 로그인: CSRF 검사 → JsonLoginFilter → AuthenticationManager → 세션 저장 → 성공 응답.
 * 이후 요청은 세션의 인증 정보로 URL별 권한을 검사한 뒤 컨트롤러로 전달된다.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(CustomUserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		// 이메일로 가져온 UserDetails의 해시와 입력 비밀번호를 BCrypt로 비교한다.
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager,
			ObjectMapper objectMapper, Validator validator, JsonResponseWriter responseWriter,
			LoginSuccessHandler successHandler) throws Exception {
		// CSRF 토큰과 로그인 상태를 서버 세션에 보관한다. 클라이언트는 JSESSIONID 쿠키로 세션을 이어간다.
		HttpSessionCsrfTokenRepository csrfRepository = new HttpSessionCsrfTokenRepository();
		SecurityContextRepository contextRepository = new DelegatingSecurityContextRepository(
			new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());

		JsonLoginFilter loginFilter = new JsonLoginFilter(authenticationManager, objectMapper, validator,
			responseWriter);
		loginFilter.setSecurityContextRepository(contextRepository);
		// 로그인 성공 시 세션 ID를 교체하고 기존 CSRF 토큰을 폐기하므로, 클라이언트는 토큰을 다시 조회한다.
		loginFilter.setSessionAuthenticationStrategy(new CompositeSessionAuthenticationStrategy(List.of(
			new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfRepository))));
		loginFilter.setAuthenticationSuccessHandler(successHandler);
		loginFilter.setAuthenticationFailureHandler((request, response, exception) ->
			responseWriter.writeError(response, ErrorCode.INVALID_CREDENTIALS));

		// permitAll은 비로그인 접근만 허용한다. 로그인·가입을 포함한 POST에는 여전히 CSRF 토큰이 필요하다.
		http
			.authenticationManager(authenticationManager)
			.securityContext(context -> context.securityContextRepository(contextRepository))
			.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
			.authorizeHttpRequests(authorize -> authorize
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/csrf", "/api/terms").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/signup/member",
					"/api/auth/signup/agent", "/api/dev/identity-verifications").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.requestMatchers("/api/agents/**").hasRole("AGENT")
				.anyRequest().authenticated())
			// JSON API이므로 기본 로그인 화면·HTTP Basic·로그인 후 페이지 이동용 요청 저장을 사용하지 않는다.
			.requestCache(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			// 인증 누락은 401, 권한 부족과 CSRF 검증 실패는 각각 구분된 403 JSON으로 응답한다.
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, exception) ->
					responseWriter.writeError(response, ErrorCode.UNAUTHENTICATED))
				.accessDeniedHandler((request, response, exception) -> responseWriter.writeError(response,
					exception instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.ACCESS_DENIED)))
			// 로그아웃도 필터에서 처리하며, 세션과 인증 정보·쿠키를 정리한 뒤 본문 없이 204를 반환한다.
			.logout(logout -> logout.logoutUrl("/api/auth/logout")
				.invalidateHttpSession(true)
				.clearAuthentication(true)
				.deleteCookies("JSESSIONID")
				.logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
			.addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
