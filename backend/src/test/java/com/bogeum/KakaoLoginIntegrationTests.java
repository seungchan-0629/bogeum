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
import com.bogeum.domain.user.model.SocialAccount;
import com.bogeum.domain.user.model.SocialAuthorization;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.JwtUtil;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

/** 카카오 HTTP만 대체하고 실제 세션·Security·회원 저장·JWT 발급을 검증한다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-kakao-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "spring.sql.init.mode=never",
	"auth.cookie-secure=false", "kakao.client-id=test-kakao-client", "kakao.client-secret=test-kakao-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(KakaoLoginIntegrationTests.KakaoHttpConfig.class)
class KakaoLoginIntegrationTests {
	@Autowired private MockMvc mvc;
	@Autowired private UserRepository users;
	@Autowired private SocialAccountRepository socials;
	@Autowired private PhoneVerificationRepository phones;
	@Autowired private PasswordEncoder encoder;
	@Autowired private JwtUtil jwt;
	@Autowired private JsonMapper mapper;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MockRestServiceServer kakao;

	@BeforeEach
	void resetKakao() { kakao.reset(); }

	@AfterEach
	void verifyKakao() { kakao.verify(); }

	@Test
	void newUserRequiresConsentAndPhoneVerificationBeforeSignupAndJwtLogin() throws Exception {
		String email = email();
		String providerId = providerId();
		Flow flow = authorize(null);
		callback(flow, providerId, "카카오 회원", email, true, true);
		mvc.perform(csrfPost("/auth/social/kakao/login").session(flow.session()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.isNewUser").value(true))
			.andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.authentication").isEmpty());
		assertThat(users.findByEmail(email)).isEmpty();
		String phoneToken = PhoneVerificationFixtures.verifiedToken(phones, "01012345678");
		var body = signupBody("변조한 이름", "changed-" + email, phoneToken);
		body.put("agreements", Map.of("privacyCollect", false, "thirdPartyProvide", true));
		mvc.perform(csrfPost("/auth/social/kakao/signup").session(flow.session()).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest());
		body.put("agreements", Map.of("privacyCollect", true, "thirdPartyProvide", true));
		body.put("phone", "01099999999");
		mvc.perform(csrfPost("/auth/social/kakao/signup").session(flow.session()).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PHONE_VERIFICATION_REQUIRED"));
		body.put("phone", "01012345678");
		var signup = mvc.perform(csrfPost("/auth/social/kakao/signup").session(flow.session()).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(body))).andExpect(status().isOk())
			.andExpect(jsonPath("$.user.lastLoginProvider").value("KAKAO")).andReturn();
		User user = users.findByEmail(email).orElseThrow();
		assertThat(user.getName()).isEqualTo("카카오 회원");
		assertThat(user.getPasswordHash()).isNull();
		assertThat(user.getIdentityVerifiedAt()).isNotNull();
		assertThat(jdbc.queryForObject("select count(*) from user_consent where user_id = ?", Long.class, user.getUserId())).isEqualTo(2);
		assertThat(socials.findByProviderAndProviderUserId(SocialProvider.KAKAO, providerId)).isPresent();
		var tokens = mapper.readTree(signup.getResponse().getContentAsString());
		mvc.perform(get("/users/me").header("Authorization", "Bearer " + tokens.path("accessToken").asText()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.passwordLoginAvailable").value(false));
		mvc.perform(csrfPost("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(Map.of("refreshToken", tokens.path("refreshToken").asText()))))
			.andExpect(status().isOk()).andExpect(jsonPath("$.user.userId").value(user.getUserId()));
		mvc.perform(csrfPost("/auth/social/kakao/signup").session(flow.session()).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@CsvSource({"true,false", "false,true", "false,false"})
	void nicknameAndUntrustedEmailAreNotUsedAsSignupIdentity(boolean valid, boolean verified) throws Exception {
		Flow flow = authorize(null);
		callback(flow, providerId(), null, email(), valid, verified);
		mvc.perform(csrfPost("/auth/social/kakao/login").session(flow.session()))
			.andExpect(jsonPath("$.name").isEmpty()).andExpect(jsonPath("$.email").isEmpty());
		String signupEmail = email();
		mvc.perform(csrfPost("/auth/social/kakao/signup").session(flow.session()).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(signupBody("직접 입력한 이름", signupEmail,
				PhoneVerificationFixtures.verifiedToken(phones, "01012345678")))))
			.andExpect(status().isOk());
		assertThat(users.findByEmail(signupEmail).orElseThrow().getName()).isEqualTo("직접 입력한 이름");
	}

	@Test
	void linkedUserLogsInByProviderIdAndConsumesTheResultOnce() throws Exception {
		User user = account(Role.USER, false);
		String id = providerId();
		link(user, SocialProvider.KAKAO, id);
		Flow flow = authorize(null);
		callback(flow, id, null, null, false, false);
		mvc.perform(csrfPost("/auth/social/kakao/login").session(flow.session()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.isNewUser").value(false))
			.andExpect(jsonPath("$.authentication.user.userId").value(user.getUserId()))
			.andExpect(jsonPath("$.authentication.user.lastLoginProvider").value("KAKAO"));
		mvc.perform(csrfPost("/auth/social/kakao/login").session(flow.session())).andExpect(status().isBadRequest());
		assertThat(users.findById(user.getUserId()).orElseThrow().getLastLoginProvider()).isEqualTo(LoginProvider.KAKAO);
	}

	@Test
	void sameEmailDoesNotAutomaticallyMergeAccountsAndNaverIdsRemainSeparate() throws Exception {
		User user = account(Role.USER, true);
		String id = providerId();
		link(user, SocialProvider.NAVER, id);
		Flow newUser = authorize(null);
		callback(newUser, id, null, email(), true, true);
		mvc.perform(csrfPost("/auth/social/kakao/login").session(newUser.session()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.isNewUser").value(true));
		Flow conflict = authorize(null);
		callback(conflict, id, null, user.getEmail(), true, true);
		mvc.perform(csrfPost("/auth/social/kakao/login").session(conflict.session()))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SOCIAL_EMAIL_CONFLICT"));
		assertThat(socials.existsByUserUserIdAndProvider(user.getUserId(), SocialProvider.KAKAO)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void linksOnlyToTheUserWhoStartedAuthenticationWithoutRequiringPassword(boolean hasPassword) throws Exception {
		User user = account(Role.USER, hasPassword);
		User other = account(Role.USER, true);
		mvc.perform(csrfPost("/auth/social/kakao/link/authorize")).andExpect(status().isUnauthorized());
		Flow flow = authorize(user);
		String id = providerId();
		callback(flow, id, null, null, false, false);
		mvc.perform(csrfPost("/auth/social/kakao/login").session(flow.session())).andExpect(status().isBadRequest());
		mvc.perform(csrfPost("/auth/social/kakao/link").session(flow.session()).header("Authorization", access(other)))
			.andExpect(status().isBadRequest());
		mvc.perform(csrfPost("/auth/social/kakao/link").session(flow.session()).header("Authorization", access(user)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.connected").value(true));
		mvc.perform(get("/auth/social/kakao/connection").header("Authorization", access(user)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.connected").value(true));
		mvc.perform(csrfPost("/auth/social/kakao/link/authorize").header("Authorization", access(user)))
			.andExpect(status().isConflict());
		Flow duplicate = authorize(other);
		callback(duplicate, id, null, null, false, false);
		mvc.perform(csrfPost("/auth/social/kakao/link").session(duplicate.session()).header("Authorization", access(other)))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SOCIAL_ALREADY_CONNECTED"));
	}

	@Test
	void rejectsAgentAdminAndWithdrawnAccounts() throws Exception {
		for (Role role : new Role[]{Role.AGENT, Role.ADMIN}) {
			User user = account(role, true);
			mvc.perform(csrfPost("/auth/social/kakao/link/authorize").header("Authorization", access(user)))
				.andExpect(status().isForbidden());
			String id = providerId();
			link(user, SocialProvider.KAKAO, id);
			Flow flow = authorize(null);
			callback(flow, id, null, null, false, false);
			mvc.perform(csrfPost("/auth/social/kakao/login").session(flow.session())).andExpect(status().isForbidden());
		}
		User withdrawn = users.saveAndFlush(User.builder().email(email()).name("탈퇴 회원").phone("01012345678")
			.role(Role.USER).userStatus(UserStatus.WITHDRAWN).build());
		String id = providerId();
		link(withdrawn, SocialProvider.KAKAO, id);
		Flow flow = authorize(null);
		callback(flow, id, null, null, false, false);
		mvc.perform(csrfPost("/auth/social/kakao/login").session(flow.session())).andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsMissingCsrfSessionStateExpirationAndCancellation() throws Exception {
		for (String path : new String[]{"authorize", "login", "signup", "link/authorize", "link"}) {
			mvc.perform(post("/auth/social/kakao/" + path)).andExpect(status().isForbidden());
		}
		mvc.perform(get("/auth/social/kakao/connection")).andExpect(status().isUnauthorized());
		mvc.perform(csrfPost("/auth/social/kakao/login")).andExpect(status().isBadRequest());
		mvc.perform(get("/auth/social/kakao/callback").param("state", "stolen").param("code", "stolen"))
			.andExpect(redirectedUrl("http://localhost:5173/login?kakao=error"));
		for (String invalid : new String[]{"wrong-state", "expired", "cancelled", "missing-state"}) {
			Flow flow = authorize(null);
			if (invalid.equals("expired")) flow.session().setAttribute("socialAuthorization",
				new SocialAuthorization(SocialProvider.KAKAO, flow.state(), Instant.now().minusSeconds(1), null));
			var request = get("/auth/social/kakao/callback").session(flow.session()).param("code", "unused");
			if (!invalid.equals("missing-state")) request.param("state", invalid.equals("wrong-state") ? "wrong" : flow.state());
			if (invalid.equals("cancelled")) request.param("error", "access_denied");
			mvc.perform(request).andExpect(redirectedUrl("http://localhost:5173/login?kakao=error"));
			assertThat(flow.session().getAttribute("socialAuthorization")).isNull();
		}
	}

	@ParameterizedTest
	@EnumSource(value = SocialProvider.class, names = {"NAVER", "GOOGLE"})
	void rejectsOtherProvidersAtEveryKakaoEndpoint(SocialProvider provider) throws Exception {
		Flow flow = authorize(null);
		var authorization = new SocialAuthorization(provider, flow.state(), Instant.now().plusSeconds(600), null);
		authorization.authenticate(providerId(), "다른 제공자", email());
		flow.session().setAttribute("socialAuthorization", authorization);
		mvc.perform(csrfPost("/auth/social/kakao/login").session(flow.session())).andExpect(status().isBadRequest());
		mvc.perform(csrfPost("/auth/social/kakao/signup").session(flow.session()).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(signupBody("다른 제공자", email(), "unused")))).andExpect(status().isBadRequest());
		User user = account(Role.USER, true);
		mvc.perform(csrfPost("/auth/social/kakao/link").session(flow.session()).header("Authorization", access(user)))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/auth/social/kakao/callback").session(flow.session()).param("state", flow.state()).param("code", "unused"))
			.andExpect(redirectedUrl("http://localhost:5173/login?kakao=error"));
	}

	@Test
	void providerFailuresAndCallbackReplayDiscardAuthentication() throws Exception {
		Flow failed = authorize(null);
		kakao.expect(requestTo("https://kauth.kakao.com/oauth/token")).andRespond(withBadRequest());
		mvc.perform(get("/auth/social/kakao/callback").session(failed.session()).param("state", failed.state()).param("code", "bad"))
			.andExpect(redirectedUrl("http://localhost:5173/login?kakao=error"));
		Flow replay = authorize(null);
		callback(replay, providerId(), null, null, false, false);
		mvc.perform(get("/auth/social/kakao/callback").session(replay.session()).param("state", replay.state()).param("code", "again"))
			.andExpect(redirectedUrl("http://localhost:5173/login?kakao=error"));
		mvc.perform(csrfPost("/auth/social/kakao/login").session(replay.session())).andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"access_token\":\"\"}", "{\"error\":\"invalid_grant\"}"})
	void invalidTokenResponsesCannotAuthenticate(String token) throws Exception {
		Flow flow = authorize(null);
		kakao.expect(requestTo("https://kauth.kakao.com/oauth/token"))
			.andRespond(withSuccess(token, MediaType.APPLICATION_JSON));
		mvc.perform(get("/auth/social/kakao/callback").session(flow.session()).param("state", flow.state()).param("code", "bad"))
			.andExpect(redirectedUrl("http://localhost:5173/login?kakao=error"));
		mvc.perform(csrfPost("/auth/social/kakao/login").session(flow.session())).andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"id\":0}", "{\"id\":-1}", "{\"id\":\"123\"}"})
	void profileWithoutNumericUserIdCannotAuthenticate(String profile) throws Exception {
		Flow flow = authorize(null);
		kakao.expect(requestTo("https://kauth.kakao.com/oauth/token"))
			.andRespond(withSuccess("{\"access_token\":\"kakao-access\"}", MediaType.APPLICATION_JSON));
		kakao.expect(requestTo("https://kapi.kakao.com/v2/user/me")).andRespond(withSuccess(profile, MediaType.APPLICATION_JSON));
		mvc.perform(get("/auth/social/kakao/callback").session(flow.session()).param("state", flow.state()).param("code", "bad"))
			.andExpect(redirectedUrl("http://localhost:5173/login?kakao=error"));
		mvc.perform(csrfPost("/auth/social/kakao/login").session(flow.session())).andExpect(status().isBadRequest());
	}

	private Flow authorize(User user) throws Exception {
		var request = csrfPost("/auth/social/kakao/" + (user == null ? "authorize" : "link/authorize"));
		if (user != null) request.header("Authorization", access(user));
		MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
		String url = mapper.readTree(result.getResponse().getContentAsString()).path("authorizationUrl").asText();
		assertThat(url).startsWith("https://kauth.kakao.com/oauth/authorize?").contains("client_id=test-kakao-client")
			.doesNotContain("test-kakao-secret");
		var query = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
		assertThat(URLDecoder.decode(query.getFirst("redirect_uri"), StandardCharsets.UTF_8))
			.isEqualTo("http://localhost:8080/auth/social/kakao/callback");
		String state = query.getFirst("state");
		assertThat(state).hasSizeGreaterThanOrEqualTo(40);
		var session = (MockHttpSession) result.getRequest().getSession(false);
		assertThat(session.getMaxInactiveInterval()).isEqualTo(600);
		return new Flow(session, state);
	}

	private void callback(Flow flow, String id, String name, String email, boolean valid, boolean verified) throws Exception {
		kakao.verify();
		kakao.reset();
		kakao.expect(requestTo("https://kauth.kakao.com/oauth/token")).andExpect(method(HttpMethod.POST))
			.andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().string(containsString("client_secret=test-kakao-secret")))
			.andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().string(containsString("code=authorization-code")))
			.andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().string(containsString("state=" + flow.state())))
			.andRespond(withSuccess("{\"access_token\":\"kakao-access\"}", MediaType.APPLICATION_JSON));
		Map<String, Object> account = new HashMap<>(Map.of("is_email_valid", valid, "is_email_verified", verified,
			"profile", Map.of("nickname", "실명이 아닌 닉네임")));
		if (name != null) account.put("name", name);
		if (email != null) account.put("email", email);
		kakao.expect(requestTo("https://kapi.kakao.com/v2/user/me")).andExpect(method(HttpMethod.GET))
			.andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("Authorization", "Bearer kakao-access"))
			.andRespond(withSuccess(mapper.writeValueAsString(Map.of("id", Long.parseLong(id), "kakao_account", account)), MediaType.APPLICATION_JSON));
		boolean isLink = ((SocialAuthorization) flow.session().getAttribute("socialAuthorization")).getUserId() != null;
		mvc.perform(get("/auth/social/kakao/callback").session(flow.session()).param("state", flow.state()).param("code", "authorization-code"))
			.andExpect(status().isFound()).andExpect(redirectedUrl("http://localhost:5173/" + (isLink ? "mypage" : "login") + "?kakao=complete"))
			.andExpect(header().string("Referrer-Policy", "no-referrer"));
	}

	private Map<String, Object> signupBody(String name, String email, String verificationToken) {
		return new HashMap<>(Map.of("name", name, "email", email, "phone", "01012345678", "verificationToken", verificationToken,
			"agreements", Map.of("privacyCollect", true, "thirdPartyProvide", true)));
	}

	private User account(Role role, boolean hasPassword) {
		return users.saveAndFlush(User.builder().email(email()).name("기존 회원").phone("01012345678").role(role)
			.passwordHash(hasPassword ? encoder.encode("local-password") : null).build());
	}

	private void link(User user, SocialProvider provider, String id) {
		socials.saveAndFlush(SocialAccount.builder().user(user).provider(provider).providerUserId(id).build());
	}

	private String access(User user) { return "Bearer " + jwt.createAccess(user.getUserId(), UUID.randomUUID().toString()); }
	private String email() { return UUID.randomUUID() + "@example.com"; }
	private String providerId() { return Long.toString(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)); }

	private MockHttpServletRequestBuilder csrfPost(String path) throws Exception {
		MvcResult result = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn();
		var body = mapper.readTree(result.getResponse().getContentAsString());
		return post(path).cookie(result.getResponse().getCookie("BOGEUM_CSRF"))
			.header(body.path("headerName").asText(), body.path("token").asText());
	}

	private record Flow(MockHttpSession session, String state) { }

	@TestConfiguration(proxyBeanMethods = false)
	static class KakaoHttpConfig {
		private final RestClient.Builder builder = RestClient.builder();
		@Bean MockRestServiceServer kakaoServer() { return MockRestServiceServer.bindTo(builder).build(); }
		@Bean @Primary @Qualifier("kakaoRestClient")
		RestClient testKakaoRestClient(MockRestServiceServer kakaoServer) { return builder.build(); }
	}
}
