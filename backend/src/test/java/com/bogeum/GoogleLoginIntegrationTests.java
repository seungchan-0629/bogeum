package com.bogeum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
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
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/** 구글 HTTP만 대체하고 실제 세션·Security·회원 저장·JWT 발급을 검증한다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-google-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "spring.sql.init.mode=never",
	"spring.config.import=", "solapi.api-key=test-key", "solapi.api-secret=test-secret", "solapi.sender-number=01000000000",
	"auth.cookie-secure=false", "google.client-id=test-google-client", "google.client-secret=test-google-secret",
	"google.redirect-uri=http://localhost:8080/auth/social/google/callback", "google.frontend-url=http://localhost:5173"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GoogleLoginIntegrationTests.GoogleHttpConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class GoogleLoginIntegrationTests {
	private final MockMvc mvc;
	private final UserRepository users;
	private final SocialAccountRepository socials;
	private final PhoneVerificationRepository phones;
	private final PasswordEncoder encoder;
	private final JwtUtil jwt;
	private final JsonMapper mapper;
	private final JdbcTemplate jdbc;
	private final MockRestServiceServer google;

	@BeforeEach
	void resetGoogle() { google.reset(); }

	@AfterEach
	void verifyGoogle() { google.verify(); }

	@Test
	void newUserRequiresConsentAndPhoneVerificationBeforeSignupAndJwtLogin() throws Exception {
		String email = email();
		String subject = UUID.randomUUID().toString();
		Flow flow = authorize(null);
		callback(flow, subject, "구글 회원", email, true);
		mvc.perform(csrfPost("/auth/social/google/login").session(flow.session()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.isNewUser").value(true))
			.andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.authentication").isEmpty());
		assertThat(users.findByEmail(email)).isEmpty();
		var body = signupBody("변조한 이름", "changed-" + email, PhoneVerificationFixtures.verifiedToken(phones, "01012345678"));
		body.put("agreements", Map.of("privacyCollect", false, "thirdPartyProvide", true));
		mvc.perform(csrfPost("/auth/social/google/signup").session(flow.session()).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest());
		body.put("agreements", Map.of("privacyCollect", true, "thirdPartyProvide", true));
		body.put("phone", "01099999999");
		mvc.perform(csrfPost("/auth/social/google/signup").session(flow.session()).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PHONE_VERIFICATION_REQUIRED"));
		body.put("phone", "01012345678");
		var signup = mvc.perform(csrfPost("/auth/social/google/signup").session(flow.session()).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(body))).andExpect(status().isOk())
			.andExpect(jsonPath("$.user.lastLoginProvider").value("GOOGLE")).andReturn();
		User user = users.findByEmail(email).orElseThrow();
		assertThat(user.getName()).isEqualTo("구글 회원");
		assertThat(user.getPasswordHash()).isNull();
		assertThat(user.getIdentityVerifiedAt()).isNotNull();
		assertThat(jdbc.queryForObject("select count(*) from user_consent where user_id = ?", Long.class, user.getUserId())).isEqualTo(2);
		assertThat(socials.findByProviderAndProviderUserId(SocialProvider.GOOGLE, subject)).isPresent();
		var tokens = mapper.readTree(signup.getResponse().getContentAsString());
		mvc.perform(get("/users/me").header("Authorization", "Bearer " + tokens.path("accessToken").asText()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.passwordLoginAvailable").value(false));
		mvc.perform(csrfPost("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(Map.of("refreshToken", tokens.path("refreshToken").asText()))))
			.andExpect(status().isOk()).andExpect(jsonPath("$.user.userId").value(user.getUserId()));
		mvc.perform(csrfPost("/auth/social/google/signup").session(flow.session()).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest());
	}

	@Test
	void missingNameAndUnverifiedEmailCanBeEnteredDuringSignup() throws Exception {
		Flow flow = authorize(null);
		callback(flow, UUID.randomUUID().toString(), null, email(), false);
		mvc.perform(csrfPost("/auth/social/google/login").session(flow.session()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.name").isEmpty()).andExpect(jsonPath("$.email").isEmpty());
		String signupEmail = email();
		mvc.perform(csrfPost("/auth/social/google/signup").session(flow.session()).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(signupBody("직접 입력한 이름", signupEmail,
				PhoneVerificationFixtures.verifiedToken(phones, "01012345678")))))
			.andExpect(status().isOk());
		assertThat(users.findByEmail(signupEmail).orElseThrow().getName()).isEqualTo("직접 입력한 이름");
	}

	@Test
	void linkedUserLogsInByStableSubjectEvenWhenEmailChangesAndConsumesResultOnce() throws Exception {
		User user = account(Role.USER, false);
		String subject = UUID.randomUUID().toString();
		link(user, SocialProvider.GOOGLE, subject);
		Flow flow = authorize(null);
		callback(flow, subject, null, email(), true);
		mvc.perform(csrfPost("/auth/social/google/login").session(flow.session()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.isNewUser").value(false))
			.andExpect(jsonPath("$.authentication.user.userId").value(user.getUserId()))
			.andExpect(jsonPath("$.authentication.user.lastLoginProvider").value("GOOGLE"));
		mvc.perform(csrfPost("/auth/social/google/login").session(flow.session())).andExpect(status().isBadRequest());
		assertThat(users.findById(user.getUserId()).orElseThrow().getLastLoginProvider()).isEqualTo(LoginProvider.GOOGLE);
	}

	@ParameterizedTest
	@EnumSource(value = SocialProvider.class, names = {"NAVER", "KAKAO"})
	void sameEmailDoesNotMergeAccountsAndOtherProviderIdsRemainSeparate(SocialProvider provider) throws Exception {
		User user = account(Role.USER, true);
		String subject = UUID.randomUUID().toString();
		link(user, provider, subject);
		Flow newUser = authorize(null);
		callback(newUser, subject, null, email(), true);
		mvc.perform(csrfPost("/auth/social/google/login").session(newUser.session()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.isNewUser").value(true));
		Flow conflict = authorize(null);
		callback(conflict, subject, null, user.getEmail(), true);
		mvc.perform(csrfPost("/auth/social/google/login").session(conflict.session()))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SOCIAL_EMAIL_CONFLICT"));
		assertThat(socials.existsByUserUserIdAndProvider(user.getUserId(), SocialProvider.GOOGLE)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void linksOnlyToTheUserWhoStartedAuthenticationAndRejectsDuplicateLinks(boolean hasPassword) throws Exception {
		User user = account(Role.USER, hasPassword);
		User other = account(Role.USER, true);
		mvc.perform(csrfPost("/auth/social/google/link/authorize")).andExpect(status().isUnauthorized());
		mvc.perform(csrfPost("/auth/social/google/link")).andExpect(status().isUnauthorized());
		Flow flow = authorize(user);
		// 구글 sub의 최대 길이도 저장할 수 있어야 한다.
		String subject = UUID.randomUUID() + "a".repeat(219);
		callback(flow, subject, null, null, false);
		mvc.perform(csrfPost("/auth/social/google/login").session(flow.session())).andExpect(status().isBadRequest());
		mvc.perform(csrfPost("/auth/social/google/link").session(flow.session()).header("Authorization", access(other)))
			.andExpect(status().isBadRequest());
		mvc.perform(csrfPost("/auth/social/google/link").session(flow.session()).header("Authorization", access(user)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.connected").value(true));
		assertThat(socials.findByProviderAndProviderUserId(SocialProvider.GOOGLE, subject)).isPresent();
		mvc.perform(get("/auth/social/google/connection").header("Authorization", access(user)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.connected").value(true));
		mvc.perform(csrfPost("/auth/social/google/link/authorize").header("Authorization", access(user)))
			.andExpect(status().isConflict());
		Flow duplicate = authorize(other);
		callback(duplicate, subject, null, null, false);
		mvc.perform(csrfPost("/auth/social/google/link").session(duplicate.session()).header("Authorization", access(other)))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SOCIAL_ALREADY_CONNECTED"));
	}

	@Test
	void rejectsAgentAdminAndWithdrawnAccounts() throws Exception {
		for (Role role : new Role[]{Role.AGENT, Role.ADMIN}) {
			User user = account(role, true);
			mvc.perform(csrfPost("/auth/social/google/link/authorize").header("Authorization", access(user)))
				.andExpect(status().isForbidden());
			String subject = UUID.randomUUID().toString();
			link(user, SocialProvider.GOOGLE, subject);
			Flow flow = authorize(null);
			callback(flow, subject, null, null, false);
			mvc.perform(csrfPost("/auth/social/google/login").session(flow.session())).andExpect(status().isForbidden());
		}
		User withdrawn = users.saveAndFlush(User.builder().email(email()).name("탈퇴 회원").phone("01012345678")
			.role(Role.USER).userStatus(UserStatus.WITHDRAWN).build());
		String subject = UUID.randomUUID().toString();
		link(withdrawn, SocialProvider.GOOGLE, subject);
		Flow flow = authorize(null);
		callback(flow, subject, null, null, false);
		mvc.perform(csrfPost("/auth/social/google/login").session(flow.session())).andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsMissingCsrfSessionStateExpirationAndCancellation() throws Exception {
		for (String path : new String[]{"authorize", "login", "signup", "link/authorize", "link"}) {
			mvc.perform(post("/auth/social/google/" + path)).andExpect(status().isForbidden());
		}
		mvc.perform(get("/auth/social/google/connection")).andExpect(status().isUnauthorized());
		mvc.perform(csrfPost("/auth/social/google/login")).andExpect(status().isBadRequest());
		mvc.perform(get("/auth/social/google/callback").param("state", "stolen").param("code", "stolen"))
			.andExpect(redirectedUrl("http://localhost:5173/login?google=error"));
		for (String invalid : new String[]{"wrong-state", "expired", "cancelled", "missing-state", "missing-code"}) {
			Flow flow = authorize(null);
			if (invalid.equals("expired")) flow.session().setAttribute("socialAuthorization",
				new SocialAuthorization(SocialProvider.GOOGLE, flow.state(), Instant.now().minusSeconds(1), null));
			var request = get("/auth/social/google/callback").session(flow.session());
			if (!invalid.equals("missing-code")) request.param("code", "unused");
			if (!invalid.equals("missing-state")) request.param("state", invalid.equals("wrong-state") ? "wrong" : flow.state());
			if (invalid.equals("cancelled")) request.param("error", "access_denied");
			mvc.perform(request).andExpect(redirectedUrl("http://localhost:5173/login?google=error"));
			assertThat(flow.session().getAttribute("socialAuthorization")).isNull();
		}
	}

	@ParameterizedTest
	@EnumSource(value = SocialProvider.class, names = {"NAVER", "KAKAO"})
	void rejectsOtherProvidersAtEveryGoogleEndpoint(SocialProvider provider) throws Exception {
		Flow flow = authorize(null);
		var authorization = new SocialAuthorization(provider, flow.state(), Instant.now().plusSeconds(600), null);
		authorization.authenticate(UUID.randomUUID().toString(), "다른 제공자", email());
		flow.session().setAttribute("socialAuthorization", authorization);
		mvc.perform(csrfPost("/auth/social/google/login").session(flow.session())).andExpect(status().isBadRequest());
		mvc.perform(csrfPost("/auth/social/google/signup").session(flow.session()).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(signupBody("다른 제공자", email(), "unused")))).andExpect(status().isBadRequest());
		User user = account(Role.USER, true);
		mvc.perform(csrfPost("/auth/social/google/link").session(flow.session()).header("Authorization", access(user)))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/auth/social/google/callback").session(flow.session()).param("state", flow.state()).param("code", "unused"))
			.andExpect(redirectedUrl("http://localhost:5173/login?google=error"));
	}

	@Test
	void providerFailuresAndCallbackReplayDiscardAuthentication() throws Exception {
		Flow failed = authorize(null);
		google.expect(requestTo("https://oauth2.googleapis.com/token")).andRespond(withBadRequest());
		mvc.perform(get("/auth/social/google/callback").session(failed.session()).param("state", failed.state()).param("code", "bad"))
			.andExpect(redirectedUrl("http://localhost:5173/login?google=error"));
		Flow replay = authorize(null);
		callback(replay, UUID.randomUUID().toString(), null, null, false);
		mvc.perform(get("/auth/social/google/callback").session(replay.session()).param("state", replay.state()).param("code", "again"))
			.andExpect(redirectedUrl("http://localhost:5173/login?google=error"));
		mvc.perform(csrfPost("/auth/social/google/login").session(replay.session())).andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"access_token\":\"\"}", "{\"error\":\"invalid_grant\"}", "{\"access_token\":123}", "null"})
	void invalidTokenResponsesCannotAuthenticate(String token) throws Exception {
		Flow flow = authorize(null);
		google.expect(requestTo("https://oauth2.googleapis.com/token")).andRespond(withSuccess(token, MediaType.APPLICATION_JSON));
		mvc.perform(get("/auth/social/google/callback").session(flow.session()).param("state", flow.state()).param("code", "bad"))
			.andExpect(redirectedUrl("http://localhost:5173/login?google=error"));
		mvc.perform(csrfPost("/auth/social/google/login").session(flow.session())).andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"sub\":\"\"}", "{\"sub\":\" \"}", "{\"sub\":123}", "null", "too-long", "unavailable"})
	void invalidOrUnavailableUserInfoCannotAuthenticate(String profile) throws Exception {
		Flow flow = authorize(null);
		google.expect(requestTo("https://oauth2.googleapis.com/token"))
			.andRespond(withSuccess("{\"access_token\":\"google-access\"}", MediaType.APPLICATION_JSON));
		if (profile.equals("unavailable")) {
			google.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo")).andRespond(withServerError());
		} else {
			String body = profile.equals("too-long") ? mapper.writeValueAsString(Map.of("sub", "a".repeat(256))) : profile;
			google.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
		}
		mvc.perform(get("/auth/social/google/callback").session(flow.session()).param("state", flow.state()).param("code", "bad"))
			.andExpect(redirectedUrl("http://localhost:5173/login?google=error"));
		mvc.perform(csrfPost("/auth/social/google/login").session(flow.session())).andExpect(status().isBadRequest());
	}

	private Flow authorize(User user) throws Exception {
		var request = csrfPost("/auth/social/google/" + (user == null ? "authorize" : "link/authorize"));
		if (user != null) request.header("Authorization", access(user));
		MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
		String url = mapper.readTree(result.getResponse().getContentAsString()).path("authorizationUrl").asText();
		assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth?").contains("client_id=test-google-client")
			.doesNotContain("test-google-secret");
		var query = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
		assertThat(URLDecoder.decode(query.getFirst("redirect_uri"), StandardCharsets.UTF_8))
			.isEqualTo("http://localhost:8080/auth/social/google/callback");
		assertThat(URLDecoder.decode(query.getFirst("scope"), StandardCharsets.UTF_8)).isEqualTo("openid email profile");
		String state = query.getFirst("state");
		assertThat(state).hasSizeGreaterThanOrEqualTo(40);
		var session = (MockHttpSession) result.getRequest().getSession(false);
		assertThat(session.getMaxInactiveInterval()).isEqualTo(600);
		return new Flow(session, state);
	}

	private void callback(Flow flow, String subject, String name, String email, boolean verified) throws Exception {
		google.verify();
		google.reset();
		google.expect(requestTo("https://oauth2.googleapis.com/token")).andExpect(method(HttpMethod.POST))
			.andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
			.andExpect(content().string(containsString("client_id=test-google-client")))
			.andExpect(content().string(containsString("client_secret=test-google-secret")))
			.andExpect(content().string(containsString("grant_type=authorization_code")))
			.andExpect(content().string(containsString("code=authorization-code")))
			.andExpect(content().string(containsString("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fauth%2Fsocial%2Fgoogle%2Fcallback")))
			.andRespond(withSuccess("{\"access_token\":\"google-access\"}", MediaType.APPLICATION_JSON));
		Map<String, Object> profile = new HashMap<>(Map.of("sub", subject, "email_verified", verified));
		if (name != null) profile.put("name", name);
		if (email != null) profile.put("email", email);
		google.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo")).andExpect(method(HttpMethod.GET))
			.andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("Authorization", "Bearer google-access"))
			.andRespond(withSuccess(mapper.writeValueAsString(profile), MediaType.APPLICATION_JSON));
		boolean isLink = ((SocialAuthorization) flow.session().getAttribute("socialAuthorization")).getUserId() != null;
		mvc.perform(get("/auth/social/google/callback").session(flow.session()).param("state", flow.state()).param("code", "authorization-code"))
			.andExpect(status().isFound()).andExpect(redirectedUrl("http://localhost:5173/" + (isLink ? "mypage" : "login") + "?google=complete"))
			.andExpect(header().string("Referrer-Policy", "no-referrer")).andExpect(header().string("Cache-Control", "no-store"));
	}

	private Map<String, Object> signupBody(String name, String email, String verificationToken) {
		return new HashMap<>(Map.of("name", name, "email", email, "phone", "01012345678", "verificationToken", verificationToken,
			"agreements", Map.of("privacyCollect", true, "thirdPartyProvide", true)));
	}

	private User account(Role role, boolean hasPassword) {
		return users.saveAndFlush(User.builder().email(email()).name("기존 회원").phone("01012345678").role(role)
			.passwordHash(hasPassword ? encoder.encode("local-password") : null).build());
	}

	private void link(User user, SocialProvider provider, String subject) {
		socials.saveAndFlush(SocialAccount.builder().user(user).provider(provider).providerUserId(subject).build());
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
	static class GoogleHttpConfig {
		private final RestClient.Builder builder = RestClient.builder();
		@Bean MockRestServiceServer googleServer() { return MockRestServiceServer.bindTo(builder).build(); }
		@Bean @Primary @Qualifier("googleRestClient")
		RestClient testGoogleRestClient(MockRestServiceServer googleServer) { return builder.build(); }
	}
}
