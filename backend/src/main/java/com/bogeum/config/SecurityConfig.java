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
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;

import com.bogeum.exception.ErrorCode;
import com.bogeum.security.CustomUserDetailsService;
import com.bogeum.security.filter.JsonLoginFilter;
import com.bogeum.security.handler.JsonResponseWriter;
import com.bogeum.security.handler.LoginSuccessHandler;
import com.bogeum.security.handler.OAuth2LoginSuccessHandler;
import com.bogeum.security.handler.OAuth2LoginFailureHandler;
import com.bogeum.security.oauth.CustomOAuth2UserService;
import com.bogeum.security.oauth.SocialAuthorizationRequestResolver;
import com.bogeum.security.oauth.SocialAuthorizationRequestRepository;

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
	HttpSessionCsrfTokenRepository csrfTokenRepository() {
		return new HttpSessionCsrfTokenRepository();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new DelegatingSecurityContextRepository(
			new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
	}

	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy(HttpSessionCsrfTokenRepository csrfRepository) {
		return new CompositeSessionAuthenticationStrategy(List.of(
			new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfRepository)));
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager,
			ObjectMapper objectMapper, Validator validator, JsonResponseWriter responseWriter,
			LoginSuccessHandler successHandler, HttpSessionCsrfTokenRepository csrfRepository,
			SecurityContextRepository contextRepository, SessionAuthenticationStrategy sessionAuthenticationStrategy,
			SocialClientRegistrations registrations, ClientRegistrationRepository clientRegistrationRepository,
			CustomOAuth2UserService oauth2UserService, OAuth2LoginSuccessHandler oauth2SuccessHandler,
			OAuth2LoginFailureHandler oauth2FailureHandler,
			SocialAuthorizationRequestRepository authorizationRequests) throws Exception {
		// CSRF 토큰과 로그인 상태를 서버 세션에 보관한다. 클라이언트는 JSESSIONID 쿠키로 세션을 이어간다.

		JsonLoginFilter loginFilter = new JsonLoginFilter(authenticationManager, objectMapper, validator,
			responseWriter);
		loginFilter.setSecurityContextRepository(contextRepository);
		// 로그인 성공 시 세션 ID를 교체하고 기존 CSRF 토큰을 폐기하므로, 클라이언트는 토큰을 다시 조회한다.
		loginFilter.setSessionAuthenticationStrategy(sessionAuthenticationStrategy);
		loginFilter.setAuthenticationSuccessHandler(successHandler);
		loginFilter.setAuthenticationFailureHandler((request, response, exception) ->
			responseWriter.writeError(response, ErrorCode.INVALID_CREDENTIALS));

		// permitAll은 비로그인 접근만 허용한다. 로그인·가입을 포함한 POST에는 여전히 CSRF 토큰이 필요하다.
		http
			.securityContext(context -> context.securityContextRepository(contextRepository))
			.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
			.authorizeHttpRequests(authorize -> authorize
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/csrf", "/api/auth/recent-login", "/api/terms").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/auth/social/providers", "/api/oauth2/authorization/**",
					"/api/login/oauth2/code/**").permitAll()
				.requestMatchers("/api/auth/social/pending", "/api/auth/social/signup", "/api/auth/social/link")
					.hasRole("SOCIAL_PENDING")
				.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/signup/member",
					"/api/auth/signup/agent", "/api/dev/identity-verifications").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.requestMatchers("/api/agents/**").hasRole("AGENT")
				// 소셜 인증만 마친 임시 사용자는 가입/연결 API 외의 회원 기능을 사용할 수 없다.
				.anyRequest().hasAnyRole("GENERAL", "AGENT", "ADMIN"))
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
		if (!registrations.isEmpty()) {
			http.oauth2Login(oauth -> oauth
				.clientRegistrationRepository(clientRegistrationRepository)
				.authorizedClientRepository(new HttpSessionOAuth2AuthorizedClientRepository())
				.authorizationEndpoint(endpoint -> endpoint.baseUri("/api/oauth2/authorization")
					.authorizationRequestRepository(authorizationRequests)
					.authorizationRequestResolver(new SocialAuthorizationRequestResolver(clientRegistrationRepository)))
				.redirectionEndpoint(endpoint -> endpoint.baseUri("/api/login/oauth2/code/*"))
				.userInfoEndpoint(endpoint -> endpoint.userService(oauth2UserService))
				.successHandler(oauth2SuccessHandler)
				.failureHandler(oauth2FailureHandler));
		}
		return http.build();
	}
}
