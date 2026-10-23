package com.bogeum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.bogeum.security.RecentLoginService.COOKIE_NAME;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.bogeum.domain.Member;
import com.bogeum.domain.MemberType;
import com.bogeum.domain.SocialAccount;
import com.bogeum.repository.*;
import com.bogeum.security.oauth.PendingSocialUser;
import com.bogeum.security.oauth.SocialProfile;
import com.bogeum.security.oauth.SocialAuthorizationRequestRepository;
import com.bogeum.security.oauth.SocialAuthorizationRequestRepository.LinkIntent;
import tools.jackson.databind.ObjectMapper;

/** 실제 Security OAuth2 필터 → HTTP 토큰/사용자 정보 응답 → DB → 세션을 검증한다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:social-test;DB_CLOSE_DELAY=-1",
	"bogeum.oauth2.google.client-id=test-google", "bogeum.oauth2.google.client-secret=test-secret",
	"bogeum.oauth2.kakao.client-id=test-kakao", "bogeum.oauth2.kakao.client-secret=test-secret",
	"bogeum.oauth2.naver.client-id=test-naver", "bogeum.oauth2.naver.client-secret=test-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SocialAuthIntegrationTests {
	private static final String PASSWORD = "Existing-password-2026!";
	private static final Map<String, String> USER_INFO = new ConcurrentHashMap<>();
	private static final AtomicInteger TOKEN_CALLS = new AtomicInteger();
	private static final HttpServer PROVIDER = providerServer();

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired MemberRepository members;
	@Autowired SocialAccountRepository socialAccounts;
	@Autowired MemberProfileRepository profiles;
	@Autowired ConsentRecordRepository consents;
	@Autowired IdentityVerificationRepository verifications;
	@Autowired PasswordEncoder passwordEncoder;
	@Autowired SocialAuthorizationRequestRepository authorizationRequests;

	@BeforeEach
	@AfterEach
	void clean() {
		socialAccounts.deleteAll();
		consents.deleteAll();
		profiles.deleteAll();
		members.deleteAll();
		verifications.deleteAll();
		USER_INFO.put("google", "{\"sub\":\"google-123\",\"email\":\"social@example.com\"}");
		USER_INFO.put("kakao", "{\"id\":123,\"kakao_account\":{\"email\":\"social@example.com\"}}");
		USER_INFO.put("naver", "{\"resultcode\":\"00\",\"response\":{\"id\":\"naver-123\",\"email\":\"social@example.com\"}}");
	}

	@AfterAll
	static void stopProvider() { PROVIDER.stop(0); }

	@Test
	void listsOnlyPublicProviderInformation() throws Exception {
		mvc.perform(get("/api/auth/social/providers")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(content().string(not(containsString("test-secret"))));
		mvc.perform(get("/api/oauth2/authorization/unknown"))
			.andExpect(status().isServiceUnavailable());
	}

	@ParameterizedTest
	@ValueSource(strings = {"google", "kakao", "naver"})
	void firstLoginOnlyCreatesPendingSessionWithNoMemberAccess(String provider) throws Exception {
		Start start = start(provider);
		String oldId = start.session().getId();
		MvcResult result = callback(start).andExpect(status().isOk())
			.andExpect(cookie().doesNotExist(COOKIE_NAME))
			.andExpect(jsonPath("$.status").value("REGISTRATION_REQUIRED"))
			.andExpect(jsonPath("$.provider").value(provider))
			.andExpect(content().string(not(containsString("provider-token"))))
			.andReturn();
		MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
		assertThat(session.getId()).isNotEqualTo(oldId);
		assertThat(members.count()).isZero();
		assertThat(socialAccounts.count()).isZero();
		mvc.perform(get("/api/auth/social/pending").session(session)).andExpect(status().isOk());
		mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isForbidden());
		mvc.perform(get("/api/admin/test").session(session)).andExpect(status().isForbidden());
		mvc.perform(get("/api/agents/me").session(session)).andExpect(status().isForbidden());
		mvc.perform(post("/api/auth/social/signup").session(session)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"google", "kakao", "naver"})
	void linkedLoginUsesProviderIdAndKeepsOriginalMember(String provider) throws Exception {
		Member member = createMember("original@example.com");
		String userId = provider.equals("kakao") ? "123" : provider + "-123";
		socialAccounts.saveAndFlush(new SocialAccount(member, provider, userId));
		MvcResult login = callback(start(provider))
			.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(member.getId()))
			.andExpect(jsonPath("$.email").value("original@example.com"))
			.andExpect(cookie().value(COOKIE_NAME, provider.toUpperCase(Locale.ROOT)))
			.andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		mvc.perform(get("/api/auth/recent-login").cookie(login.getResponse().getCookie(COOKIE_NAME)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.loginType").value(provider.toUpperCase(Locale.ROOT)))
			.andExpect(jsonPath("$.available").value(true));
		mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(member.getId()));
		assertThat(members.count()).isEqualTo(1);
		mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void callbackAndAccountLinkBothInvalidatePreviousCsrfToken() throws Exception {
		createMember("existing@example.com");
		MvcResult initial = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
		MockHttpSession session = (MockHttpSession) initial.getRequest().getSession(false);
		String beforeOAuth = mapper.readTree(initial.getResponse().getContentAsString()).get("token").asText();
		callback(start("google", session)).andExpect(status().isOk());
		String body = credentials("existing@example.com", PASSWORD);
		mvc.perform(post("/api/auth/social/link").session(session).header("X-CSRF-TOKEN", beforeOAuth)
			.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		MvcResult refreshed = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
		String beforeLink = mapper.readTree(refreshed.getResponse().getContentAsString()).get("token").asText();
		mvc.perform(post("/api/auth/social/link").session(session).header("X-CSRF-TOKEN", beforeLink)
			.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
		mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", beforeLink))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		MvcResult after = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
		String afterLink = mapper.readTree(after.getResponse().getContentAsString()).get("token").asText();
		mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", afterLink))
			.andExpect(status().isNoContent());
	}

	@Test
	void rejectsMissingSessionWrongStateDeniedConsentAndCallbackReplay() throws Exception {
		Start start = start("google");
		int calls = TOKEN_CALLS.get();
		mvc.perform(get("/api/login/oauth2/code/google").param("code", "code").param("state", start.state()))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SOCIAL_LOGIN_FAILED"));
		mvc.perform(get("/api/login/oauth2/code/google").session(start.session())
			.param("code", "code").param("state", "wrong-state")).andExpect(status().isUnauthorized());
		assertThat(TOKEN_CALLS.get()).isEqualTo(calls);
		Start denied = start("google");
		mvc.perform(get("/api/login/oauth2/code/google").session(denied.session())
			.param("error", "access_denied").param("state", denied.state()))
			.andExpect(status().isUnauthorized()).andExpect(cookie().doesNotExist(COOKIE_NAME));
		Start accepted = start("google");
		callback(accepted).andExpect(status().isOk());
		callback(accepted).andExpect(status().isUnauthorized());
		assertThat(members.count()).isZero();
	}

	@Test
	void suspendedMemberAndMalformedProviderIdentityCannotLogin() throws Exception {
		Member member = createMember("suspended@example.com");
		member.suspend();
		members.saveAndFlush(member);
		socialAccounts.saveAndFlush(new SocialAccount(member, "google", "google-123"));
		callback(start("google")).andExpect(status().isUnauthorized());
		USER_INFO.put("naver", "{\"response\":{\"email\":\"someone@example.com\"}}");
		callback(start("naver")).andExpect(status().isUnauthorized());
	}

	@Test
	void signupRequiresIdentityAndConsentsThenRotatesSessionAndCanLoginAgain() throws Exception {
		// 카카오 이메일 동의가 없어도 추가 가입에서 본인 인증한 이메일을 받는다.
		USER_INFO.put("kakao", "{\"id\":123}");
		MockHttpSession session = pendingSession("kakao");
		String oldId = session.getId();
		String token = identityToken("new@example.com");
		Map<String, Object> body = signupBody("new@example.com", token);
		body.put("consents", List.of(Map.of("type", "PRIVACY_COLLECTION", "version", "general-dev-v1", "agreed", true)));
		mvc.perform(post("/api/auth/social/signup").session(session).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"))
			.andExpect(cookie().doesNotExist(COOKIE_NAME));
		assertThat(members.count()).isZero();
		assertThat(socialAccounts.count()).isZero();
		mvc.perform(post("/api/auth/social/signup").session(session).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(signupBody("other@example.com", token))))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IDENTITY_VERIFICATION"));
		MvcResult signup = mvc.perform(post("/api/auth/social/signup").session(session).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(signupBody("new@example.com", token))))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.memberType").value("GENERAL"))
			.andExpect(cookie().value(COOKIE_NAME, "KAKAO")).andReturn();
		long id = mapper.readTree(signup.getResponse().getContentAsString()).get("id").asLong();
		assertThat(session.getId()).isNotEqualTo(oldId);
		assertThat(members.count()).isEqualTo(1);
		assertThat(profiles.count()).isEqualTo(1);
		assertThat(consents.count()).isEqualTo(2);
		assertThat(socialAccounts.count()).isEqualTo(1);
		mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
		mvc.perform(post("/api/auth/social/signup").session(session).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(signupBody("new@example.com", token))))
			.andExpect(status().isForbidden());
		callback(start("kakao")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
	}

	@Test
	void matchingEmailDoesNotMergeAndExplicitLinkRequiresExistingPassword() throws Exception {
		Member member = createMember("social@example.com");
		MockHttpSession session = pendingSession("google");
		mvc.perform(post("/api/auth/social/signup").session(session).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(signupBody("social@example.com", "unused-token"))))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
		mvc.perform(post("/api/auth/social/link").session(session).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(credentials("social@example.com", "wrong-password")))
			.andExpect(status().isUnauthorized()).andExpect(cookie().doesNotExist(COOKIE_NAME));
		assertThat(socialAccounts.count()).isZero();
		mvc.perform(post("/api/auth/social/link").session(session).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(credentials("social@example.com", PASSWORD)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(member.getId()))
			.andExpect(cookie().value(COOKIE_NAME, "GOOGLE"));
		assertThat(members.count()).isEqualTo(1);
		callback(start("google")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(member.getId()));
	}

	@Test
	void sameProviderCannotReplaceExistingLinkAndOldPendingCannotStealLinkedAccount() throws Exception {
		Member first = createMember("first@example.com");
		createMember("second@example.com");
		MockHttpSession pendingOne = pendingSession("google");
		MockHttpSession pendingTwo = pendingSession("google");
		mvc.perform(post("/api/auth/social/link").session(pendingOne).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(credentials("first@example.com", PASSWORD)))
			.andExpect(status().isOk());
		mvc.perform(post("/api/auth/social/link").session(pendingTwo).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(credentials("second@example.com", PASSWORD)))
			.andExpect(status().isConflict());
		USER_INFO.put("google", "{\"sub\":\"different-google\"}");
		MockHttpSession different = pendingSession("google");
		mvc.perform(post("/api/auth/social/link").session(different).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(credentials("first@example.com", PASSWORD)))
			.andExpect(status().isConflict());
		assertThat(socialAccounts.findByProviderAndProviderUserId("google", "google-123").orElseThrow()
			.getMember().getId()).isEqualTo(first.getId());
		assertThat(socialAccounts.count()).isEqualTo(1);
	}

	@Test
	void expiredPendingAndForgedSignupCannotCreateAccount() throws Exception {
		var pending = new PendingSocialUser(new SocialProfile("google", "expired", ""), Instant.now().minusSeconds(1));
		var auth = new OAuth2AuthenticationToken(pending, pending.getAuthorities(), "google");
		mvc.perform(get("/api/auth/social/pending").with(authentication(auth))).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("SOCIAL_REGISTRATION_EXPIRED"));
		mvc.perform(post("/api/auth/social/signup").with(authentication(auth)).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(signupBody("new@example.com", "token"))))
			.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/auth/social/signup").with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(signupBody("new@example.com", "token"))))
			.andExpect(status().isUnauthorized());
		assertThat(members.count()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = {"google", "kakao", "naver"})
	void loggedInMemberCanLinkThenLoginWithEitherPasswordOrSocial(String provider) throws Exception {
		Member member = createMember("existing@example.com");
		String passwordHash = member.getPasswordHash();
		MockHttpSession session = login(member.getEmail());
		mvc.perform(get("/api/auth/social/accounts").session(session)).andExpect(status().isOk())
			.andExpect(content().json("[]"));
		Start link = startLink(provider, session);
		String oldId = session.getId();
		assertThat(socialAccounts.count()).isZero();
		callback(link).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(member.getId()))
			.andExpect(jsonPath("$.email").value(member.getEmail()))
			.andExpect(cookie().doesNotExist(COOKIE_NAME));
		assertThat(session.getId()).isNotEqualTo(oldId);
		assertThat(members.count()).isEqualTo(1);
		assertThat(members.findById(member.getId()).orElseThrow().getPasswordHash()).isEqualTo(passwordHash);
		assertThat(profiles.count()).isZero();
		assertThat(consents.count()).isZero();
		mvc.perform(get("/api/auth/social/accounts").session(session)).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].provider").value(provider))
			.andExpect(jsonPath("$[0].linkedAt").isNotEmpty())
			.andExpect(jsonPath("$[0].providerUserId").doesNotExist())
			.andExpect(jsonPath("$[0].member").doesNotExist());
		callback(link).andExpect(status().isUnauthorized());
		assertCurrentMember(session, member);
		callback(start(provider)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(member.getId()));
		assertCurrentMember(login(member.getEmail()), member);
	}

	@Test
	void linkStartRequiresMemberCsrfValidPasswordAndConfiguredProvider() throws Exception {
		Member member = createMember("existing@example.com");
		String body = mapper.writeValueAsString(Map.of("password", PASSWORD));
		mvc.perform(post("/api/auth/social/link/google").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isUnauthorized());
		MockHttpSession pending = pendingSession("google");
		mvc.perform(post("/api/auth/social/link/google").session(pending).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
		mvc.perform(get("/api/auth/social/accounts").session(pending)).andExpect(status().isForbidden());
		mvc.perform(get("/api/auth/social/accounts")).andExpect(status().isUnauthorized());
		MockHttpSession session = login(member.getEmail());
		mvc.perform(post("/api/auth/social/link/google").session(session)
			.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		mvc.perform(post("/api/auth/social/link/google").session(session).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong-password\"}"))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		for (String password : List.of("", "가".repeat(25))) {
			mvc.perform(post("/api/auth/social/link/google").session(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("password", password))))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		}
		mvc.perform(post("/api/auth/social/link/unknown").session(session).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SOCIAL_LOGIN_UNAVAILABLE"));
		assertThat(socialAccounts.count()).isZero();
		assertCurrentMember(session, member);
	}

	@Test
	void linkingAnotherMembersSocialAccountFailsWithoutSwitchingLoggedInMember() throws Exception {
		Member owner = createMember("owner@example.com");
		Member target = createMember("target@example.com");
		socialAccounts.saveAndFlush(new SocialAccount(owner, "google", "google-123"));
		MockHttpSession session = login(target.getEmail());
		callback(startLink("google", session)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SOCIAL_ACCOUNT_ALREADY_LINKED"));
		assertCurrentMember(session, target);
		mvc.perform(get("/api/auth/social/accounts").session(session)).andExpect(content().json("[]"));
		assertThat(socialAccounts.findByProviderAndProviderUserId("google", "google-123").orElseThrow()
			.getMember().getId()).isEqualTo(owner.getId());
		assertThat(socialAccounts.count()).isEqualTo(1);
	}

	@Test
	void cannotReplaceExistingProviderAtStartOrAfterConcurrentLink() throws Exception {
		Member member = createMember("existing@example.com");
		MockHttpSession session = login(member.getEmail());
		Start started = startLink("google", session);
		socialAccounts.saveAndFlush(new SocialAccount(member, "google", "other-google"));
		callback(started).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SOCIAL_ACCOUNT_ALREADY_LINKED"));
		mvc.perform(post("/api/auth/social/link/google").session(session).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("password", PASSWORD))))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SOCIAL_ACCOUNT_ALREADY_LINKED"));
		assertCurrentMember(session, member);
		assertThat(socialAccounts.findByProviderAndProviderUserId("google", "google-123")).isEmpty();
		assertThat(socialAccounts.count()).isEqualTo(1);
	}

	@Test
	void deniedMalformedOrWrongStateLinkPreservesLocalSession() throws Exception {
		Member member = createMember("existing@example.com");
		MockHttpSession session = login(member.getEmail());
		Start denied = startLink("google", session);
		int calls = TOKEN_CALLS.get();
		mvc.perform(get("/api/login/oauth2/code/google").session(session)
			.param("code", "code").param("state", "wrong-state")).andExpect(status().isUnauthorized());
		assertThat(TOKEN_CALLS.get()).isEqualTo(calls);
		assertCurrentMember(session, member);
		mvc.perform(get("/api/login/oauth2/code/google").session(session)
			.param("error", "access_denied").param("state", denied.state())).andExpect(status().isUnauthorized());
		assertCurrentMember(session, member);
		callback(denied).andExpect(status().isUnauthorized());
		USER_INFO.put("google", "{\"email\":\"missing-id@example.com\"}");
		callback(startLink("google", session)).andExpect(status().isUnauthorized());
		assertCurrentMember(session, member);
		assertThat(socialAccounts.count()).isZero();
		// 취소한 연결 의도가 이후 일반 소셜 로그인에 남아 있지 않는다.
		callback(start("naver", session)).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REGISTRATION_REQUIRED"));
		assertThat(socialAccounts.count()).isZero();
	}

	@Test
	void linkExpiresAndCannotBeReused() throws Exception {
		Member member = createMember("existing@example.com");
		MockHttpSession session = login(member.getEmail());
		Start started = startLink("google", session);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setSession(session);
		request.setParameter("state", started.state());
		var authorization = authorizationRequests.loadAuthorizationRequest(request);
		authorizationRequests.saveAuthorizationRequest(OAuth2AuthorizationRequest.from(authorization)
			.attributes(attributes -> attributes.put(SocialAuthorizationRequestRepository.LINK_INTENT,
				new LinkIntent(member.getId(), "google", Instant.now().minusSeconds(1)))).build(),
			request, new MockHttpServletResponse());
		int calls = TOKEN_CALLS.get();
		callback(started).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SOCIAL_LINK_EXPIRED"));
		assertThat(TOKEN_CALLS.get()).isEqualTo(calls);
		assertCurrentMember(session, member);
		callback(started).andExpect(status().isUnauthorized());
		assertThat(socialAccounts.count()).isZero();
	}

	@Test
	void accountSwitchOrSuspensionDuringLinkCannotAttachSocialIdentity() throws Exception {
		Member first = createMember("first@example.com");
		Member second = createMember("second@example.com");
		MockHttpSession session = login(first.getEmail());
		Start started = startLink("google", session);
		mvc.perform(post("/api/auth/login").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(credentials(second.getEmail(), PASSWORD))).andExpect(status().isOk());
		callback(started).andExpect(status().isUnauthorized());
		assertCurrentMember(session, second);
		Start suspended = startLink("google", session);
		second.suspend();
		members.saveAndFlush(second);
		callback(suspended).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/auth/social/accounts").session(session)).andExpect(status().isUnauthorized());
		assertThat(socialAccounts.count()).isZero();
	}

	@Test
	void newLinkRequestSupersedesOldRequestAndSuccessfulCallbackRotatesCsrf() throws Exception {
		Member member = createMember("existing@example.com");
		MockHttpSession session = login(member.getEmail());
		Start old = startLink("google", session);
		Start current = startLink("kakao", session);
		callback(old).andExpect(status().isUnauthorized());
		MvcResult csrfResult = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
		String token = mapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asText();
		callback(current).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(member.getId()));
		mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", token))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		assertCurrentMember(session, member);
		assertThat(socialAccounts.count()).isEqualTo(1);
		assertThat(socialAccounts.findByProviderAndProviderUserId("google", "google-123")).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"AGENT", "ADMIN"})
	void linkingPreservesExistingMemberRole(String type) throws Exception {
		Member member = members.saveAndFlush(new Member("role@example.com", passwordEncoder.encode(PASSWORD), MemberType.valueOf(type)));
		MockHttpSession session = login(member.getEmail());
		callback(startLink("google", session)).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(member.getId())).andExpect(jsonPath("$.memberType").value(type));
		callback(start("google")).andExpect(status().isOk()).andExpect(jsonPath("$.memberType").value(type));
	}

	private MockHttpSession login(String email) throws Exception {
		return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(credentials(email, PASSWORD)))
			.andExpect(status().isOk()).andReturn().getRequest().getSession(false);
	}

	private void assertCurrentMember(MockHttpSession session, Member member) throws Exception {
		mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(member.getId()));
	}

	private Start startLink(String provider, MockHttpSession session) throws Exception {
		MvcResult result = mvc.perform(post("/api/auth/social/link/" + provider).session(session).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("password", PASSWORD))))
			.andExpect(status().isOk()).andExpect(jsonPath("$.expiresAt").isNotEmpty())
			.andExpect(content().string(not(containsString(PASSWORD))))
			.andExpect(content().string(not(containsString("test-secret")))).andReturn();
		String url = mapper.readTree(result.getResponse().getContentAsString()).get("authorizationUrl").asText();
		String state = Arrays.stream(URI.create(url).getRawQuery().split("&")).filter(item -> item.startsWith("state="))
			.map(item -> URLDecoder.decode(item.substring(6), StandardCharsets.UTF_8)).findFirst().orElseThrow();
		return new Start(provider, session, state);
	}

	private Member createMember(String email) {
		return members.saveAndFlush(new Member(email, passwordEncoder.encode(PASSWORD), MemberType.GENERAL));
	}

	private String credentials(String email, String password) {
		return mapper.writeValueAsString(Map.of("email", email, "password", password));
	}

	private Map<String, Object> signupBody(String email, String token) {
		return new java.util.HashMap<>(Map.of("email", email, "identityVerificationToken", token,
			"consents", List.of(
				Map.of("type", "PRIVACY_COLLECTION", "version", "general-dev-v1", "agreed", true),
				Map.of("type", "THIRD_PARTY_PROVISION", "version", "general-dev-v1", "agreed", true))));
	}

	private String identityToken(String email) throws Exception {
		MvcResult result = mvc.perform(post("/api/dev/identity-verifications").with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
				Map.of("email", email, "name", "테스트", "phoneNumber", "01012345678"))))
			.andExpect(status().isCreated()).andReturn();
		return mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
	}

	private Start start(String provider) throws Exception {
		return start(provider, null);
	}

	private Start start(String provider, MockHttpSession session) throws Exception {
		var request = get("/api/oauth2/authorization/" + provider);
		if (session != null) request.session(session);
		MvcResult result = mvc.perform(request)
			.andExpect(status().is3xxRedirection()).andReturn();
		String query = URI.create(result.getResponse().getRedirectedUrl()).getRawQuery();
		String state = Arrays.stream(query.split("&")).filter(item -> item.startsWith("state="))
			.map(item -> URLDecoder.decode(item.substring(6), StandardCharsets.UTF_8)).findFirst().orElseThrow();
		return new Start(provider, (MockHttpSession) result.getRequest().getSession(false), state);
	}

	private org.springframework.test.web.servlet.ResultActions callback(Start start) throws Exception {
		return mvc.perform(get("/api/login/oauth2/code/" + start.provider()).session(start.session())
			.param("code", "test-code").param("state", start.state()));
	}

	private MockHttpSession pendingSession(String provider) throws Exception {
		return (MockHttpSession) callback(start(provider)).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REGISTRATION_REQUIRED")).andReturn().getRequest().getSession(false);
	}

	private record Start(String provider, MockHttpSession session, String state) { }

	private static HttpServer providerServer() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/token", exchange -> {
				TOKEN_CALLS.incrementAndGet();
				respond(exchange, "{\"access_token\":\"provider-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
			});
			server.createContext("/userinfo/", exchange -> respond(exchange,
				USER_INFO.get(exchange.getRequestURI().getPath().substring("/userinfo/".length()))));
			server.start();
			return server;
		} catch (IOException exception) { throw new IllegalStateException(exception); }
	}

	private static void respond(com.sun.net.httpserver.HttpExchange exchange, String json) throws IOException {
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, bytes.length);
		try (var body = exchange.getResponseBody()) { body.write(bytes); }
		exchange.close();
	}

	@TestConfiguration
	static class ProviderConfiguration {
		@Bean
		@Primary
		ClientRegistrationRepository testClientRegistrations() {
			String base = "http://127.0.0.1:" + PROVIDER.getAddress().getPort();
			return new InMemoryClientRegistrationRepository(List.of("google", "kakao", "naver").stream()
				.map(provider -> ClientRegistration.withRegistrationId(provider)
					.clientId("test-client").clientSecret("test-secret")
					.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
					.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
					.redirectUri("{baseUrl}/api/login/oauth2/code/{registrationId}")
					.authorizationUri(base + "/authorize").tokenUri(base + "/token")
					.userInfoUri(base + "/userinfo/" + provider)
					.userNameAttributeName(provider.equals("naver") ? "response" : provider.equals("google") ? "sub" : "id")
					.build()).toList());
		}
	}
}
