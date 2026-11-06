package com.bogeum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bogeum.common.enums.LoginProvider;
import com.bogeum.common.enums.Role;
import com.bogeum.common.enums.SocialProvider;
import com.bogeum.common.enums.UserStatus;
import com.bogeum.domain.user.PhoneVerificationRepository;
import com.bogeum.domain.user.SocialAccountRepository;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.SocialAuthorization;
import com.bogeum.domain.user.model.SocialAccount;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.JwtUtil;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/** 네이버 HTTP만 대체하고 실제 컨트롤러·세션·Security·JWT·H2를 함께 검증한다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-naver-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "spring.sql.init.mode=never",
	"auth.cookie-secure=false", "naver.client-id=test-client", "naver.client-secret=test-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(NaverLoginIntegrationTests.NaverHttpConfig.class)
class NaverLoginIntegrationTests {
	@Autowired private MockMvc mvc;
	@Autowired private UserRepository users;
	@Autowired private UserService userService;
	@Autowired private SocialAccountRepository socials;
	@Autowired private PhoneVerificationRepository phones;
	@Autowired private PasswordEncoder encoder;
	@Autowired private JwtUtil jwt;
	@Autowired private JsonMapper mapper;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MockRestServiceServer naver;

	@BeforeEach
	void resetNaver() { naver.reset(); }

	@AfterEach
	void verifyNaver() { naver.verify(); }

	@Test
	@DisplayName("신규 네이버 사용자는 동의·문자 인증 후 비밀번호 없이 가입하고 JWT로 회원 정보를 조회한다")
	void newUserSignupAndLogin() throws Exception {
		String email = email();
		Flow flow = authorize(null);
		assertThat(flow.session().getMaxInactiveInterval()).isEqualTo(600);
		assertThat(flow.state()).hasSizeGreaterThanOrEqualTo(40);
		callback(flow, UUID.randomUUID().toString(), "네이버 회원", email);
		mvc.perform(csrfPost("/auth/social/naver/login").session(flow.session()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.isNewUser").value(true))
			.andExpect(jsonPath("$.name").value("네이버 회원"))
			.andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.authentication").isEmpty());
		assertThat(users.findByEmail(email)).isEmpty();

		String phoneToken = PhoneVerificationFixtures.verifiedToken(phones, "01012345678");
		Map<String, Object> body = signupBody("브라우저에서 바꾼 이름", "changed-" + email, phoneToken);
		body.put("agreements", Map.of("privacyCollect", false, "thirdPartyProvide", true));
		mvc.perform(csrfPost("/auth/social/naver/signup").session(flow.session()).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest());
		assertThat(users.findByEmail(email)).isEmpty();
		body.put("agreements", Map.of("privacyCollect", true, "thirdPartyProvide", true));
		body.put("phone", "01099999999");
		mvc.perform(csrfPost("/auth/social/naver/signup").session(flow.session()).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PHONE_VERIFICATION_REQUIRED"));
		body.put("phone", "01012345678");
		MvcResult signup = mvc.perform(csrfPost("/auth/social/naver/signup").session(flow.session())
			.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.user.lastLoginProvider").value("NAVER"))
			.andExpect(jsonPath("$.user.role").value("USER")).andExpect(jsonPath("$.accessToken").isString()).andReturn();
		User user = users.findByEmail(email).orElseThrow();
		assertThat(user.getPasswordHash()).isNull();
		assertThat(user.getName()).isEqualTo("네이버 회원");
		assertThat(user.getIdentityVerifiedAt()).isNotNull();
		assertThat(jdbc.queryForObject("select count(*) from user_consent where user_id = ?", Long.class, user.getUserId())).isEqualTo(2);
		var data = mapper.readTree(signup.getResponse().getContentAsString());
		mvc.perform(get("/users/me").header("Authorization", "Bearer " + data.path("accessToken").asText()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.passwordLoginAvailable").value(false));
		mvc.perform(csrfPost("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(Map.of("refreshToken", data.path("refreshToken").asText()))))
			.andExpect(status().isOk()).andExpect(jsonPath("$.user.userId").value(user.getUserId()));
		mvc.perform(csrfPost("/auth/social/naver/signup").session(flow.session()).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SOCIAL_AUTH_EXPIRED"));
	}

	@Test
	@DisplayName("네이버가 이름·이메일을 제공하지 않으면 추가 폼에서 받은 정보로 가입한다")
	void missingProfile() throws Exception {
		Flow flow = authorize(null);
		callback(flow, UUID.randomUUID().toString(), null, null);
		String email = email();
		mvc.perform(csrfPost("/auth/social/naver/signup").session(flow.session()).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(signupBody("추가 입력", email,
				PhoneVerificationFixtures.verifiedToken(phones, "01012345678")))))
			.andExpect(status().isOk());
		assertThat(users.findByEmail(email).orElseThrow().getName()).isEqualTo("추가 입력");
	}

	@Test
	@DisplayName("기존 연결은 이메일이 달라져도 제공자 식별자로 로그인하고 일회용 결과를 재사용할 수 없다")
	void existingLinkedUser() throws Exception {
		User user = account(Role.USER, false);
		String providerId = UUID.randomUUID().toString();
		link(user, providerId);
		Flow flow = authorize(null);
		callback(flow, providerId, "변경된 네이버 이름", email());
		mvc.perform(csrfPost("/auth/social/naver/login").session(flow.session()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.isNewUser").value(false))
			.andExpect(jsonPath("$.authentication.user.userId").value(user.getUserId()))
			.andExpect(jsonPath("$.authentication.user.lastLoginProvider").value("NAVER"));
		mvc.perform(csrfPost("/auth/social/naver/login").session(flow.session())).andExpect(status().isBadRequest());
		assertThat(users.findById(user.getUserId()).orElseThrow().getLastLoginProvider()).isEqualTo(LoginProvider.NAVER);
	}

	@Test
	@DisplayName("같은 이메일의 일반 계정을 자동으로 합치거나 새로 만들지 않는다")
	void emailCollision() throws Exception {
		User user = account(Role.USER, true);
		Flow flow = authorize(null);
		callback(flow, UUID.randomUUID().toString(), "네이버 회원", user.getEmail());
		mvc.perform(csrfPost("/auth/social/naver/login").session(flow.session()))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SOCIAL_EMAIL_CONFLICT"));
		assertThat(socials.existsByUserUserIdAndProvider(user.getUserId(), SocialProvider.NAVER)).isFalse();
	}

	@Test
	@DisplayName("비밀번호 입력 없이 로그인한 계정에 연결하고 다른 회원이 인증 결과를 사용할 수 없다")
	void linkToCurrentUserOnly() throws Exception {
		User user = account(Role.USER, true);
		User other = account(Role.USER, true);
		mvc.perform(csrfPost("/auth/social/naver/link/authorize"))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		Flow flow = authorize(user);
		String providerId = UUID.randomUUID().toString();
		callback(flow, providerId, "다른 프로필 이름", email());
		mvc.perform(csrfPost("/auth/social/naver/login").session(flow.session())).andExpect(status().isBadRequest());
		mvc.perform(csrfPost("/auth/social/naver/link").session(flow.session()).header("Authorization", access(other)))
			.andExpect(status().isBadRequest());
		mvc.perform(csrfPost("/auth/social/naver/link").session(flow.session()).header("Authorization", access(user)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.connected").value(true));
		mvc.perform(get("/auth/social/naver/connection").header("Authorization", access(user)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.connected").value(true));
		assertThat(socials.findByProviderAndProviderUserId(SocialProvider.NAVER, providerId).orElseThrow().getUser().getUserId())
			.isEqualTo(user.getUserId());
		mvc.perform(csrfPost("/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(Map.of("email", user.getEmail(), "password", "local-password"))))
			.andExpect(status().isOk());
		mvc.perform(csrfPost("/auth/social/naver/link").session(flow.session()).header("Authorization", access(user)))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("비밀번호가 없는 일반 회원도 로그인 상태와 네이버 인증으로 연결할 수 있다")
	void linkWithoutLocalPassword() throws Exception {
		User user = account(Role.USER, false);
		Flow flow = authorize(user);
		String providerId = UUID.randomUUID().toString();
		callback(flow, providerId, null, null);
		mvc.perform(csrfPost("/auth/social/naver/link").session(flow.session()).header("Authorization", access(user)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.connected").value(true));
		assertThat(socials.findByProviderAndProviderUserId(SocialProvider.NAVER, providerId).orElseThrow().getUser().getUserId())
			.isEqualTo(user.getUserId());
	}

	@Test
	@DisplayName("다른 계정에 연결된 네이버 계정을 가져오거나 이미 연결된 계정을 다시 연결할 수 없다")
	void duplicateLink() throws Exception {
		User owner = account(Role.USER, true);
		String providerId = UUID.randomUUID().toString();
		link(owner, providerId);
		mvc.perform(csrfPost("/auth/social/naver/link/authorize").header("Authorization", access(owner)))
			.andExpect(status().isConflict());
		User other = account(Role.USER, true);
		Flow flow = authorize(other);
		callback(flow, providerId, null, null);
		mvc.perform(csrfPost("/auth/social/naver/link").session(flow.session()).header("Authorization", access(other)))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SOCIAL_ALREADY_CONNECTED"));
		assertThat(socials.existsByUserUserIdAndProvider(other.getUserId(), SocialProvider.NAVER)).isFalse();
	}

	@Test
	@DisplayName("중개사·관리자는 네이버 연결과 소셜 로그인을 사용할 수 없고 탈퇴 계정도 로그인할 수 없다")
	void rolesAndInactiveUser() throws Exception {
		for (Role role : new Role[]{Role.AGENT, Role.ADMIN}) {
			User user = account(role, true);
			mvc.perform(csrfPost("/auth/social/naver/link/authorize").header("Authorization", access(user)))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SOCIAL_ROLE_NOT_ALLOWED"));
			String providerId = UUID.randomUUID().toString();
			link(user, providerId);
			Flow flow = authorize(null);
			callback(flow, providerId, null, null);
			mvc.perform(csrfPost("/auth/social/naver/login").session(flow.session())).andExpect(status().isForbidden());
		}
		User withdrawn = users.saveAndFlush(User.builder().email(email()).name("탈퇴 회원").phone("01012345678")
			.role(Role.USER).userStatus(UserStatus.WITHDRAWN).build());
		String providerId = UUID.randomUUID().toString();
		link(withdrawn, providerId);
		Flow flow = authorize(null);
		callback(flow, providerId, null, null);
		mvc.perform(csrfPost("/auth/social/naver/login").session(flow.session())).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("CSRF·세션·state 누락, 만료와 사용자 취소는 네이버 API 호출이나 가입 없이 거절한다")
	void invalidAuthorization() throws Exception {
		for (String path : new String[]{"authorize", "login", "signup", "link/authorize", "link"}) {
			mvc.perform(post("/auth/social/naver/" + path)).andExpect(status().isForbidden());
		}
		mvc.perform(get("/auth/social/naver/connection")).andExpect(status().isUnauthorized());
		mvc.perform(csrfPost("/auth/social/naver/login")).andExpect(status().isBadRequest());
		mvc.perform(get("/auth/social/naver/callback").param("code", "stolen").param("state", "stolen"))
			.andExpect(redirectedUrl("http://localhost:5173/login?naver=error"));
		Flow wrongState = authorize(null);
		mvc.perform(get("/auth/social/naver/callback").session(wrongState.session()).param("code", "code").param("state", "wrong"))
			.andExpect(redirectedUrl("http://localhost:5173/login?naver=error"));
		Flow expired = authorize(null);
		expired.session().setAttribute("socialAuthorization", new SocialAuthorization(SocialProvider.NAVER,
			expired.state(), Instant.now().minusSeconds(1), null));
		mvc.perform(get("/auth/social/naver/callback").session(expired.session()).param("code", "code").param("state", expired.state()))
			.andExpect(redirectedUrl("http://localhost:5173/login?naver=error"));
		Flow cancelled = authorize(null);
		mvc.perform(get("/auth/social/naver/callback").session(cancelled.session()).param("error", "access_denied")
			.param("state", cancelled.state())).andExpect(redirectedUrl("http://localhost:5173/login?naver=error"));
	}

	@Test
	@DisplayName("네이버 오류와 재사용된 콜백은 계정을 만들지 않고 안전한 화면으로 돌아간다")
	void providerFailureAndReplayedCallback() throws Exception {
		Flow failed = authorize(null);
		naver.expect(requestTo("https://nid.naver.com/oauth2.0/token"))
			.andRespond(withSuccess("{\"error\":\"invalid_grant\"}", MediaType.APPLICATION_JSON));
		mvc.perform(get("/auth/social/naver/callback").session(failed.session()).param("state", failed.state()).param("code", "bad"))
			.andExpect(redirectedUrl("http://localhost:5173/login?naver=error"));
		Flow flow = authorize(null);
		callback(flow, UUID.randomUUID().toString(), null, null);
		mvc.perform(get("/auth/social/naver/callback").session(flow.session()).param("state", flow.state()).param("code", "replayed"))
			.andExpect(redirectedUrl("http://localhost:5173/login?naver=error"));
		mvc.perform(csrfPost("/auth/social/naver/login").session(flow.session())).andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@EnumSource(SocialProvider.class)
	@DisplayName("공통 가입·로그인·연결은 인증 정보의 제공자와 로그인 수단을 그대로 사용한다")
	void commonSocialAccountFlow(SocialProvider provider) {
		String providerId = UUID.randomUUID().toString();
		var authorization = userService.createSocialAuthorization(provider, null);
		authorization.consumeState();
		authorization.authenticate(providerId, "소셜 회원", email());
		var signup = userService.signupSocial(authorization, "소셜 회원", authorization.getEmail(), "01012345678",
			PhoneVerificationFixtures.verifiedToken(phones, "01012345678"), true, true);
		User user = signup.getUser();
		assertThat(user.getLastLoginProvider()).isEqualTo(LoginProvider.valueOf(provider.name()));
		assertThat(userService.isSocialConnected(user.getUserId(), provider)).isTrue();
		assertThat(socials.findByProviderAndProviderUserId(provider, providerId).orElseThrow().getUser().getUserId())
			.isEqualTo(user.getUserId());
		assertThat(userService.loginSocial(authorization).getUser().getUserId()).isEqualTo(user.getUserId());

		User existing = account(Role.USER, true);
		var connection = userService.createSocialAuthorization(provider, existing.getUserId());
		connection.consumeState();
		connection.authenticate(UUID.randomUUID().toString(), null, null);
		userService.connectSocial(existing.getUserId(), connection);
		assertThat(userService.isSocialConnected(existing.getUserId(), provider)).isTrue();
		for (SocialProvider other : SocialProvider.values()) {
			if (other != provider) {
				assertThat(userService.isSocialConnected(existing.getUserId(), other)).isFalse();
			}
		}
	}

	@ParameterizedTest
	@EnumSource(value = SocialProvider.class, names = {"KAKAO", "GOOGLE"})
	@DisplayName("다른 제공자의 인증 정보는 네이버 API에서 처리하지 않는다")
	void rejectsDifferentProvider(SocialProvider provider) throws Exception {
		Flow flow = authorize(null);
		var authorization = new SocialAuthorization(provider, flow.state(), Instant.now().plusSeconds(600), null);
		authorization.authenticate(UUID.randomUUID().toString(), "다른 제공자", email());
		flow.session().setAttribute("socialAuthorization", authorization);
		mvc.perform(csrfPost("/auth/social/naver/login").session(flow.session()))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SOCIAL_AUTH_EXPIRED"));
		mvc.perform(csrfPost("/auth/social/naver/signup").session(flow.session()).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(signupBody("다른 제공자", authorization.getEmail(), "unused-token"))))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SOCIAL_AUTH_EXPIRED"));
		User user = account(Role.USER, true);
		mvc.perform(csrfPost("/auth/social/naver/link").session(flow.session()).header("Authorization", access(user)))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SOCIAL_AUTH_EXPIRED"));
		mvc.perform(get("/auth/social/naver/callback").session(flow.session())
			.param("state", flow.state()).param("code", "unused-code"))
			.andExpect(redirectedUrl("http://localhost:5173/login?naver=error"));
	}

	private Flow authorize(User user) throws Exception {
		var request = csrfPost("/auth/social/naver/" + (user == null ? "authorize" : "link/authorize"));
		if (user != null) request.header("Authorization", access(user));
		MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
		String url = mapper.readTree(result.getResponse().getContentAsString()).path("authorizationUrl").asText();
		assertThat(url).startsWith("https://nid.naver.com/oauth2.0/authorize?").contains("client_id=test-client");
		assertThat(url).doesNotContain("test-secret");
		String state = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
		return new Flow((MockHttpSession) result.getRequest().getSession(false), state);
	}

	private void callback(Flow flow, String providerId, String name, String email) throws Exception {
		naver.verify();
		naver.reset();
		naver.expect(requestTo("https://nid.naver.com/oauth2.0/token")).andExpect(method(HttpMethod.POST))
			.andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().string(containsString("client_secret=test-secret")))
			.andRespond(withSuccess("{\"access_token\":\"naver-access\"}", MediaType.APPLICATION_JSON));
		Map<String, String> profile = new HashMap<>();
		profile.put("id", providerId);
		if (name != null) profile.put("name", name);
		if (email != null) profile.put("email", email);
		naver.expect(requestTo("https://openapi.naver.com/v1/nid/me")).andExpect(method(HttpMethod.GET))
			.andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("Authorization", "Bearer naver-access"))
			.andRespond(withSuccess(mapper.writeValueAsString(Map.of("resultcode", "00", "response", profile)), MediaType.APPLICATION_JSON));
		boolean isLink = ((SocialAuthorization) flow.session().getAttribute("socialAuthorization")).getUserId() != null;
		MvcResult result = mvc.perform(get("/auth/social/naver/callback").session(flow.session())
			.param("state", flow.state()).param("code", "authorization-code"))
			.andExpect(status().isFound()).andExpect(redirectedUrl("http://localhost:5173/" + (isLink ? "mypage" : "login") + "?naver=complete"))
			.andExpect(header().string("Referrer-Policy", "no-referrer")).andReturn();
		assertThat(result.getResponse().getContentAsString()).isEmpty();
	}

	private Map<String, Object> signupBody(String name, String email, String verificationToken) {
		return new HashMap<>(Map.of("name", name, "email", email, "phone", "01012345678", "verificationToken", verificationToken,
			"agreements", Map.of("privacyCollect", true, "thirdPartyProvide", true)));
	}

	private User account(Role role, boolean hasPassword) {
		return users.saveAndFlush(User.builder().email(email()).name("기존 회원").phone("01012345678").role(role)
			.passwordHash(hasPassword ? encoder.encode("local-password") : null).build());
	}

	private void link(User user, String providerId) {
		socials.saveAndFlush(SocialAccount.builder().user(user).provider(SocialProvider.NAVER).providerUserId(providerId).build());
	}

	private String access(User user) { return "Bearer " + jwt.createAccess(user.getUserId(), UUID.randomUUID().toString()); }
	private String email() { return UUID.randomUUID() + "@example.com"; }

	private MockHttpServletRequestBuilder csrfPost(String path) throws Exception {
		MvcResult result = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn();
		var body = mapper.readTree(result.getResponse().getContentAsString());
		return post(path).cookie(result.getResponse().getCookie("BOGEUM_CSRF"))
			.header(body.path("headerName").asText(), body.path("token").asText());
	}

	private record Flow(MockHttpSession session, String state) { }

	@TestConfiguration(proxyBeanMethods = false)
	static class NaverHttpConfig {
		private final RestClient.Builder builder = RestClient.builder();
		@Bean MockRestServiceServer naverServer() { return MockRestServiceServer.bindTo(builder).build(); }
		@Bean @Primary @org.springframework.beans.factory.annotation.Qualifier("naverRestClient")
		RestClient testNaverRestClient(MockRestServiceServer naverServer) { return builder.build(); }
	}
}
