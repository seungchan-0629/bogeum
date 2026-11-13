package com.bogeum.config;

import com.bogeum.domain.agent.AgentService;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
		"/auth/login", "/auth/refresh", "/auth/logout",
		"/auth/signup/agent", "/auth/csrf", "/auth/signup/user", "/api/health",
		"/auth/identity-verification", "/auth/identity-verification/confirm",
		"/auth/social/naver/authorize", "/auth/social/naver/callback",
		"/auth/social/naver/login", "/auth/social/naver/signup",
		"/auth/social/kakao/authorize", "/auth/social/kakao/callback",
		"/auth/social/kakao/login", "/auth/social/kakao/signup",
		"/auth/social/google/authorize", "/auth/social/google/callback",
		"/auth/social/google/login", "/auth/social/google/signup");

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
			AgentService agentService,
			@Value("${auth.cookie-secure:true}") boolean cookieSecure) throws Exception {
		var csrfRepository = new CookieCsrfTokenRepository();
		csrfRepository.setCookieName("BOGEUM_CSRF");
		csrfRepository.setCookieCustomizer(cookie -> cookie.path("/").httpOnly(true)
			.secure(cookieSecure).sameSite("Lax"));
		var publicRequests = new OrRequestMatcher(PUBLIC_PATHS.stream()
			.map(path -> PathPatternRequestMatcher.withDefaults().matcher(path)).toArray(RequestMatcher[]::new));
		// 관리자가 승인한 중개사만 중개 업무를 할 수 있다 (AGENT_002)
		AuthorizationManager<RequestAuthorizationContext> approvedAgent = (authentication, context) -> {
			var current = authentication.get();
			boolean allowed = current.getPrincipal() instanceof LoginUser principal
				&& current.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_AGENT"))
				&& agentService.isApproved(principal.getUserId());
			return new AuthorizationDecision(allowed);
		};

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
				.requestMatchers(HttpMethod.GET, "/admin/agents").hasRole("ADMIN")
				.requestMatchers(HttpMethod.HEAD, "/admin/agents").hasRole("ADMIN")
				.requestMatchers(HttpMethod.GET, "/admin/risk-users").hasRole("ADMIN")
				.requestMatchers(HttpMethod.HEAD, "/admin/risk-users").hasRole("ADMIN")
				.requestMatchers(HttpMethod.POST, "/admin/risk-users").hasRole("ADMIN")
				.requestMatchers(HttpMethod.PATCH, "/admin/risk-users/*").hasRole("ADMIN")
				// 중개 신청은 일반 사용자가 하고, 중개사가 받아서 관리한다 (PROPERTY_002, PROPERTY_008)
				// 매물 목록은 임대인·임차인(USER)과 중개사(AGENT)가 함께 본다 (PROPERTY_004, PROPERTY_009)
				.requestMatchers(HttpMethod.GET, "/property").authenticated()
				// 내 매물 목록은 중개를 신청하는 사용자만 본다. 아래 /property/* 규칙에 먼저 걸리지 않게 앞에 둔다 (PROPERTY_013)
				.requestMatchers(HttpMethod.GET, "/property/mine").hasRole("USER")
				// 공개 중인 매물 상세도 같은 사용자가 본다. *는 한 단계라 /property/*/details 규칙과 겹치지 않는다 (PROPERTY_003)
				.requestMatchers(HttpMethod.GET, "/property/*").authenticated()
				// 관심 매물은 집을 찾는 사용자만 담는다 (FAVORITE_001)
				.requestMatchers(HttpMethod.PUT, "/favorites/*").hasRole("USER")
				.requestMatchers(HttpMethod.DELETE, "/favorites/*").hasRole("USER")
				// 내 관심 매물 목록과 담은 매물 번호 (FAVORITE_003)
				.requestMatchers(HttpMethod.GET, "/favorites/me", "/favorites/me/ids").hasRole("USER")
				.requestMatchers(HttpMethod.POST, "/property/brokerage-requests").hasRole("USER")
				.requestMatchers(HttpMethod.GET, "/property/brokerage-requests/received").access(approvedAgent)
				.requestMatchers(HttpMethod.POST, "/property/brokerage-requests/*/accept").access(approvedAgent)
				// 맡은 매물인지는 서비스 조회 조건에서 확인한다 (PROPERTY_010)
				.requestMatchers(HttpMethod.GET, "/property/*/details").access(approvedAgent)
				.requestMatchers(HttpMethod.PUT, "/property/*/details").access(approvedAgent)
				.requestMatchers(HttpMethod.POST, "/property/*/images").access(approvedAgent)
				.requestMatchers(HttpMethod.DELETE, "/property/*/images/*").access(approvedAgent)
				.requestMatchers(HttpMethod.POST, "/property/*/publish").access(approvedAgent)
				// 맡은 매물인지는 서비스 조회 조건에서 확인한다 (PROPERTY_011)
				.requestMatchers(HttpMethod.POST, "/contract-details").access(approvedAgent)
				// 본인이 중개한 계약만 서비스 조회 조건으로 거른다 (AGENT_003)
				.requestMatchers(HttpMethod.GET, "/contract-details").access(approvedAgent)
				.requestMatchers(HttpMethod.GET, "/contract/candidates").access(approvedAgent)
				// 일반 회원의 접수만 허용하고 계약 임차인 여부는 AS 서비스에서 확인한다 (AS_001).
				.requestMatchers(HttpMethod.POST, "/as-requests").hasRole("USER")
				// 임대인·임차인은 같은 USER 역할이다. 매물 소유 여부는 AS 조회 조건에서 검사한다 (AS_002).
				.requestMatchers(HttpMethod.GET, "/as-requests/received", "/as-requests/*").hasRole("USER")
				// 댓글도 USER 역할과 실제 매물 소유 관계를 모두 확인하며 기존 CSRF 검증을 유지한다 (AS_003).
				.requestMatchers(HttpMethod.POST, "/as-requests/*/comments").hasRole("USER")
				.requestMatchers(HttpMethod.PATCH, "/as-requests/*/status").hasRole("USER")
				.requestMatchers("/api/v1/agents/me", "/api/v1/agents/me/demo-approval", "/api/v1/agents/me/documents", "/api/v1/agents/me/documents/**").hasRole("AGENT")
				.requestMatchers("/api/v1/agents/**").access(approvedAgent)
				// 관리자 코드
				.requestMatchers(HttpMethod.POST, "/openbanking/tokens/institution", "/openbanking/remittances",
					"/openbanking/accounts/*/withdrawals", "/openbanking/transfers/results").hasRole("ADMIN")
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
