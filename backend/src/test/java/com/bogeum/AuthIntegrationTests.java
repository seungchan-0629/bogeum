package com.bogeum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.bogeum.domain.Member;
import com.bogeum.domain.MemberType;
import com.bogeum.repository.MemberRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTests {

	private static final String PASSWORD = "Test-password-2026!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		memberRepository.deleteAll();
		memberRepository.save(new Member("member@example.com", passwordEncoder.encode(PASSWORD), MemberType.GENERAL));
		memberRepository.save(new Member("agent@example.com", passwordEncoder.encode(PASSWORD), MemberType.AGENT));
	}

	@Test
	void noSocialKeysKeepsLocalLoginAvailableAndReportsSocialUnavailable() throws Exception {
		mockMvc.perform(get("/api/auth/social/providers"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/oauth2/authorization/google"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("SOCIAL_LOGIN_UNAVAILABLE"));
		MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/login").with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(credentials("member@example.com", PASSWORD)))
			.andExpect(status().isOk()).andReturn().getRequest().getSession(false);
		mockMvc.perform(post("/api/auth/social/link/google").session(session).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("password", PASSWORD))))
			.andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SOCIAL_LOGIN_UNAVAILABLE"));
		mockMvc.perform(get("/api/auth/social/accounts").session(session)).andExpect(status().isOk())
			.andExpect(content().json("[]"));
		mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("member@example.com"));
	}

	@Test
	void healthRemainsPublicAndProtectedApiRequiresLogin() throws Exception {
		mockMvc.perform(get("/api/health")).andExpect(status().isOk());
		mockMvc.perform(get("/api/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@ParameterizedTest
	@EnumSource(value = MemberType.class, names = {"GENERAL", "AGENT"})
	void bothMemberTypesUseTheSameLoginAndPersistSession(MemberType type) throws Exception {
		String email = type == MemberType.GENERAL ? "member@example.com" : "agent@example.com";
		CsrfSession initial = loadCsrf(null);
		String oldSessionId = initial.session().getId();

		MvcResult login = mockMvc.perform(post("/api/auth/login")
			.session(initial.session())
			.header(initial.headerName(), initial.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(credentials("  " + email.toUpperCase() + "  ", PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.memberType").value(type.name()))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andReturn();

		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		assertThat(session).isNotNull();
		assertThat(session.getId()).isNotEqualTo(oldSessionId);
		mockMvc.perform(get("/api/auth/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email));

		mockMvc.perform(post("/api/auth/logout").session(session)
			.header(initial.headerName(), initial.token()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));

		CsrfSession refreshed = loadCsrf(session);
		assertThat(refreshed.token()).isNotEqualTo(initial.token());
		mockMvc.perform(post("/api/auth/logout").session(session)
			.header(refreshed.headerName(), refreshed.token()))
			.andExpect(status().isNoContent());
		assertThat(session.isInvalid()).isTrue();
		mockMvc.perform(get("/api/auth/me").cookie(new jakarta.servlet.http.Cookie("JSESSIONID", session.getId())))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void loginWithoutCsrfIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content(credentials("member@example.com", PASSWORD)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	@Test
	void incorrectPasswordUnknownEmailAndSuspendedAccountHaveTheSameFailure() throws Exception {
		Member suspended = new Member("suspended@example.com", passwordEncoder.encode(PASSWORD), MemberType.GENERAL);
		suspended.suspend();
		memberRepository.save(suspended);
		String expected = null;
		for (Map.Entry<String, String> attempt : Map.of(
			"member@example.com", "wrong-password", "unknown@example.com", PASSWORD,
			"suspended@example.com", PASSWORD).entrySet()) {
			MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(credentials(attempt.getKey(), attempt.getValue())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andReturn();
			if (expected != null) {
				assertThat(result.getResponse().getContentAsString()).isEqualTo(expected);
			}
			expected = result.getResponse().getContentAsString();
			assertThat(result.getRequest().getSession(false)).isNull();
		}
	}

	@Test
	void repeatedUnknownLoginsNeverCreateMembers() throws Exception {
		long count = memberRepository.count();
		for (int i = 0; i < 3; i++) {
			String email = "unregistered-" + i + "@example.com";
			mockMvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(credentials(email, PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
			assertThat(memberRepository.existsByEmail(email)).isFalse();
			assertThat(memberRepository.count()).isEqualTo(count);
		}
	}

	@Test
	void repeatedSuccessfulLoginsKeepTheSameMemberIdAndCount() throws Exception {
		long count = memberRepository.count();
		Long id = memberRepository.findByEmail("member@example.com").orElseThrow().getId();
		for (int i = 0; i < 3; i++) {
			MockHttpSession session = login("member@example.com");
			mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
			assertThat(memberRepository.count()).isEqualTo(count);
		}
	}

	@Test
	void rejectsInvalidJsonAndContentTypeWithoutExposingCredentials() throws Exception {
		for (String body : new String[] {"{", "null", "[]", ""}) {
			mockMvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_JSON"));
		}
		mockMvc.perform(post("/api/auth/login").with(csrf())
			.contentType(MediaType.TEXT_PLAIN).content("password=secret"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
		mockMvc.perform(post("/api/auth/login").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(credentials("invalid-email", "do-not-expose-this")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.email").exists())
			.andExpect(content().string(not(containsString("do-not-expose-this"))));
	}

	@Test
	void validatesUtf8PasswordByteLimitAndMissingFields() throws Exception {
		mockMvc.perform(post("/api/auth/login").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(credentials("member@example.com", "가".repeat(25))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.password").exists());
		mockMvc.perform(post("/api/auth/login").with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.email").exists())
			.andExpect(jsonPath("$.fieldErrors.password").exists());
	}

	@Test
	void generalAndAgentAccountsCannotAccessAdminEndpoints() throws Exception {
		for (String email : new String[] {"member@example.com", "agent@example.com"}) {
			MockHttpSession session = login(email);
			mockMvc.perform(get("/api/admin/agents").session(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		}
	}

	@Test
	void currentMemberReadsTheCurrentAccountStatus() throws Exception {
		MockHttpSession session = login("member@example.com");
		Member member = memberRepository.findByEmail("member@example.com").orElseThrow();
		member.suspend();
		memberRepository.save(member);
		mockMvc.perform(get("/api/auth/me").session(session))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	private MockHttpSession login(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(credentials(email, PASSWORD)))
			.andExpect(status().isOk()).andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private CsrfSession loadCsrf(MockHttpSession session) throws Exception {
		var request = get("/api/auth/csrf");
		if (session != null) {
			request.session(session);
		}
		MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
		JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
		return new CsrfSession((MockHttpSession) result.getRequest().getSession(false),
			json.get("headerName").asText(), json.get("token").asText());
	}

	private String credentials(String email, String password) {
		return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
	}

	private record CsrfSession(MockHttpSession session, String headerName, String token) {
	}
}
