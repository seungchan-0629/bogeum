package com.bogeum;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.bogeum.common.enums.Role;
import com.bogeum.common.enums.UserStatus;
import com.bogeum.domain.user.model.UserDto.AuthResult;
import com.bogeum.domain.user.UserService;
import com.bogeum.utils.JwtUtil;
import com.bogeum.domain.user.model.User;
import com.bogeum.domain.user.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

/** 실제 Security 필터 + JPA/H2 + JWT를 연결한 통합 테스트. 비밀번호는 테스트에서 생성한다. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:bogeum-security-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
    "spring.sql.init.mode=never", "auth.cookie-secure=false"
})
@org.springframework.context.annotation.Import(AuthIntegrationTests.AdminAuthCheckController.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTests {
	private static final SecretKey KEY = Keys.hmacShaKeyFor(
		"12345678901234567890123456789012345678901234567890".getBytes(StandardCharsets.UTF_8));
	@org.springframework.test.context.bean.override.mockito.MockitoSpyBean
	private com.bogeum.domain.agent.AgentRepository agents;
	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbc;
	private final MockMvc mvc;
	private final UserRepository users;
	private final PasswordEncoder encoder;
	private final UserService auth;
	private final JwtUtil jwt;
	private final JsonMapper mapper;

	@Autowired
	AuthIntegrationTests(MockMvc mvc, UserRepository users, PasswordEncoder encoder,
			UserService auth, JwtUtil jwt, JsonMapper mapper) {
		this.mvc = mvc;
		this.users = users;
		this.encoder = encoder;
		this.auth = auth;
		this.jwt = jwt;
		this.mapper = mapper;
	}

	@Test
	@DisplayName("회원가입은 필수 동의를 저장하고 해시를 응답하지 않는다")
	void signupAndConsent() throws Exception {
		String email = UUID.randomUUID() + "@example.com";
		String password = UUID.randomUUID().toString();
		mvc.perform(csrfPost("/api/v1/auth/signup/user").contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(Map.of("email", email, "password", password,
				"name", "학습자", "phone", "01000000000", "agreements",
				Map.of("privacyCollect", true, "thirdPartyProvide", true)))))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.userId").isNumber())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
		User saved = users.findByEmail(email).orElseThrow();
		assertThat(saved.getRole()).isEqualTo(Role.USER);
		assertThat(encoder.matches(password, saved.getPasswordHash())).isTrue();
	}

	@Test
	@DisplayName("필수 동의 거절 시 가입하지 않는다")
	void rejectsMissingAgreement() throws Exception {
		mvc.perform(csrfPost("/api/v1/auth/signup/user").contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(Map.of("email", "no-consent@example.com", "password", UUID.randomUUID().toString(),
				"name", "학습자", "phone", "01000000000", "agreements",
				Map.of("privacyCollect", false, "thirdPartyProvide", true)))))
			.andExpect(status().isBadRequest());
		assertThat(users.findByEmail("no-consent@example.com")).isEmpty();
	}

	@Test
	@DisplayName("실제 CSRF 쿠키+마스킹 헤더로 로그인하고 토큰을 JSON으로 받는다")
	void loginWithRealCsrfAndCookie() throws Exception {
		Account account = account(Role.USER, UserStatus.ACTIVE, true);
		MvcResult csrfResult = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
		Cookie csrfCookie = csrfResult.getResponse().getCookie("BOGEUM_CSRF");
		var csrfData = mapper.readTree(csrfResult.getResponse().getContentAsString());
		MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
			.cookie(csrfCookie).header(csrfData.get("headerName").asText(), csrfData.get("token").asText())
			.contentType(MediaType.APPLICATION_JSON).content(credentials(account)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString())
			.andExpect(jsonPath("$.user.userId").value(account.user().getUserId()))
			.andExpect(jsonPath("$.user.lastLoginProvider").value("LOCAL"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.refreshToken").isString()).andExpect(jsonPath("$.refreshExpiresAt").isString()).andReturn();
		assertThat(loginResult.getResponse().getContentAsString()).doesNotContain(account.password(), "passwordHash");
		assertThat(loginResult.getResponse().getCookie("BOGEUM_REFRESH")).isNull();
		var body = mapper.readTree(loginResult.getResponse().getContentAsString());
		var claims = jwt.validateAccessToken(body.get("accessToken").asText());
		assertThat(claims).doesNotContainKeys("pw", "password", "passwordHash", "email");
	}

	@Test
	@DisplayName("CSRF 없는 로그인과 갱신은 403 공통 오류로 거부한다")
	void csrfIsRequired() throws Exception {
		for (String path : new String[]{"/login", "/refresh", "/logout", "/signup/user"}) {
			mvc.perform(post("/api/v1/auth" + path).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		}
	}

	@Test
	@DisplayName("누락된 로그인 필드는 400, 잘못된 비밀번호는 401이다")
	void invalidLogin() throws Exception {
		Account account = account(Role.USER, UserStatus.ACTIVE, true);
		mvc.perform(csrfPost("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest());
		mvc.perform(csrfPost("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(Map.of("email", account.user().getEmail(), "password", "wrong"))))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
	}

	@Test
	@DisplayName("탈퇴 계정과 소셜 전용 계정은 일반 로그인이 불가능하다")
	void disabledAndSocialAccounts() throws Exception {
		for (Account account : new Account[]{account(Role.USER, UserStatus.WITHDRAWN, true), account(Role.USER, UserStatus.ACTIVE, false)}) {
			mvc.perform(csrfPost("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(account)))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
		}
	}

	@Test
	@DisplayName("미인증 내 정보 조회는 401이고 공개 상태 조회는 가능하다")
	void anonymousAccess() throws Exception {
		mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	@DisplayName("잘못되거나 만료된 인증 헤더는 공개 API를 막지 않고 보호 API에서는 거절된다")
	void publicEndpointsIgnoreInvalidAccessToken() throws Exception {
		AuthResult original = login(account(Role.USER, UserStatus.ACTIVE, true));
		String expired = expiredToken(original, "access");
		for (String authorization : new String[]{"invalid-header", "Bearer bad-token", "Bearer " + expired}) {
			mvc.perform(get("/api/health").header("Authorization", authorization)).andExpect(status().isOk());
			mvc.perform(get("/api/v1/auth/csrf").header("Authorization", authorization))
				.andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
			String email = UUID.randomUUID() + "@example.com";
			String password = UUID.randomUUID().toString();
			mvc.perform(csrfPost("/api/v1/auth/signup/user").header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
					"email", email, "password", password, "name", "학습자", "phone", "01000000000",
					"agreements", Map.of("privacyCollect", true, "thirdPartyProvide", true)))))
				.andExpect(status().isCreated());
			MvcResult loggedIn = mvc.perform(csrfPost("/api/v1/auth/login").header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString()).andReturn();
			MvcResult refreshed = mvc.perform(csrfPost("/api/v1/auth/refresh").header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON).content(refreshBody(responseRefresh(loggedIn))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString()).andReturn();
			mvc.perform(csrfPost("/api/v1/auth/logout").header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON).content(refreshBody(responseRefresh(refreshed))))
				.andExpect(status().isNoContent());
			mvc.perform(get("/api/v1/auth/me").header("Authorization", authorization))
				.andExpect(status().isUnauthorized());
		}
	}

	@Test
	@DisplayName("유효 Access Token으로 자기 정보만 조회하고 USER는 관리자 API에 접근할 수 없다")
	void ownInformationAndRoleCheck() throws Exception {
		Account account = account(Role.USER, UserStatus.ACTIVE, true);
		AuthResult result = login(account);
		mvc.perform(get("/api/v1/auth/me").param("userId", "999999").header("Authorization", "Bearer " + result.getAccessToken()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(account.user().getUserId()))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
		mvc.perform(get("/api/v1/admin/auth-check").header("Authorization", "Bearer " + result.getAccessToken()))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}

	@Test
	@DisplayName("ADMIN으로 인증한 요청은 관리자 API를 조회한다")
	void adminAccess() throws Exception {
		AuthResult result = login(account(Role.ADMIN, UserStatus.ACTIVE, true));
		mvc.perform(get("/api/v1/admin/auth-check").header("Authorization", "Bearer " + result.getAccessToken()))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("회원이 삭제되면 기존 Access와 Refresh Token을 모두 거절한다")
	void rejectsTokensForMissingUser() throws Exception {
		Account account = account(Role.USER, UserStatus.ACTIVE, true);
		AuthResult result = login(account);
		users.deleteById(account.user().getUserId());
		mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + result.getAccessToken()))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		mvc.perform(csrfPost("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content(refreshBody(result.getRefreshToken())))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
	}

	@Test
	@DisplayName("로그인 이후 탈퇴한 회원의 기존 Access와 Refresh Token을 거절한다")
	void rejectsTokensAfterWithdrawal() throws Exception {
		Account account = account(Role.USER, UserStatus.ACTIVE, true);
		AuthResult result = login(account);
		User user = users.findById(account.user().getUserId()).orElseThrow();
		org.springframework.test.util.ReflectionTestUtils.setField(user, "userStatus", UserStatus.WITHDRAWN);
		users.saveAndFlush(user);
		mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + result.getAccessToken()))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		mvc.perform(csrfPost("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content(refreshBody(result.getRefreshToken())))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
	}

	@Test
	@DisplayName("토큰의 회원 번호가 숫자가 아니면 서버 오류 대신 토큰 오류로 응답한다")
	void rejectsInvalidUserIdClaim() throws Exception {
		String token = Jwts.builder().issuer("bogeum").subject("invalid-user-id")
			.claim("sid", UUID.randomUUID().toString()).claim("tokenType", "access")
			.expiration(Date.from(Instant.now().plusSeconds(60))).signWith(KEY, Jwts.SIG.HS256).compact();
		mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCESS_TOKEN_INVALID"));
	}

	@Test
	@DisplayName("만료 Access Token은 갱신 가능한 구분 코드로 응답한다")
	void expiredAccess() throws Exception {
		AuthResult result = login(account(Role.USER, UserStatus.ACTIVE, true));
		String expired = expiredToken(result, "access");
		mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expired))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCESS_TOKEN_EXPIRED"));
	}

	@Test
	@DisplayName("변조 토큰과 Refresh Token으로 API 접근을 거절한다")
	void tamperedAndWrongType() throws Exception {
		AuthResult result = login(account(Role.USER, UserStatus.ACTIVE, true));
		String access = result.getAccessToken();
		int signature = access.lastIndexOf('.') + 1;
		String tampered = access.substring(0, signature) + (access.charAt(signature) == 'A' ? 'B' : 'A') + access.substring(signature + 1);
		for (String token : new String[]{tampered, result.getRefreshToken(), "bad-token"}) {
			mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCESS_TOKEN_INVALID"));
		}
	}

	@Test
	@DisplayName("동일 Refresh Token을 재사용해 새 Access를 발급하고 Refresh 만료는 유지한다")
	void reusesRefreshWithoutExtendingExpiration() throws Exception {
		AuthResult original = login(account(Role.USER, UserStatus.ACTIVE, true));
		var originalClaims = jwt.validateRefreshToken(original.getRefreshToken());
		String previousAccess = original.getAccessToken();
		for (int attempt = 0; attempt < 2; attempt++) {
			MvcResult refreshed = mvc.perform(csrfPost("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON).content(refreshBody(original.getRefreshToken())))
				.andExpect(status().isOk()).andExpect(jsonPath("$.user.userId").value(original.getUser().getUserId()))
				.andReturn();
			var body = mapper.readTree(refreshed.getResponse().getContentAsByteArray());
			String access = body.get("accessToken").asText();
			assertThat(access).isNotEqualTo(previousAccess);
			assertThat(responseRefresh(refreshed)).isEqualTo(original.getRefreshToken());
			assertThat(Instant.parse(body.get("refreshExpiresAt").asText()))
				.isEqualTo(originalClaims.getExpiration().toInstant());
			var accessClaims = jwt.validateAccessToken(access);
			assertThat(accessClaims.getSubject()).isEqualTo(originalClaims.getSubject());
			assertThat(accessClaims.get("sid", String.class)).isEqualTo(originalClaims.get("sid", String.class));
			mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
				.andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(original.getUser().getUserId()));
			previousAccess = access;
		}
	}

	@Test
	@DisplayName("Refresh JSON 누락·변조·만료·Access 용도 토큰은 갱신에 사용할 수 없다")
	void invalidRefresh() throws Exception {
		AuthResult result = login(account(Role.USER, UserStatus.ACTIVE, true));
		mvc.perform(csrfPost("/api/v1/auth/refresh")).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
		mvc.perform(csrfPost("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
		String refresh = result.getRefreshToken();
		int signature = refresh.lastIndexOf('.') + 1;
		String tampered = refresh.substring(0, signature) + (refresh.charAt(signature) == 'A' ? 'B' : 'A')
			+ refresh.substring(signature + 1);
		for (String token : new String[]{expiredToken(result, "refresh"), result.getAccessToken(), tampered, "bad-token", ""}) {
			mvc.perform(csrfPost("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(token)))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
		}
	}

	@Test
	@DisplayName("무상태 로그아웃은 성공하지만 이미 발급된 Access와 Refresh를 서버에서 폐기하지 않는다")
	void logoutDoesNotRevokeStatelessTokens() throws Exception {
		AuthResult result = login(account(Role.USER, UserStatus.ACTIVE, true));
		mvc.perform(csrfPost("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content(refreshBody(result.getRefreshToken())))
			.andExpect(status().isNoContent());
		mvc.perform(csrfPost("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(result.getRefreshToken())))
			.andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").value(result.getRefreshToken()));
		mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + result.getAccessToken()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(result.getUser().getUserId()));
	}

	@Test
	@DisplayName("동일 Refresh Token을 동시에 사용하면 각 요청에 다른 Access를 발급한다")
	void concurrentRefresh() throws Exception {
		AuthResult original = login(account(Role.USER, UserStatus.ACTIVE, true));
		var executor = Executors.newFixedThreadPool(2);
		var start = new CountDownLatch(1);
		try {
			java.util.concurrent.Callable<AuthResult> task = () -> {
				start.await();
				return auth.refresh(original.getRefreshToken());
			};
			var first = executor.submit(task);
			var second = executor.submit(task);
			start.countDown();
			AuthResult firstResult = first.get(10, TimeUnit.SECONDS);
			AuthResult secondResult = second.get(10, TimeUnit.SECONDS);
			assertThat(firstResult.getRefreshToken()).isEqualTo(original.getRefreshToken());
			assertThat(secondResult.getRefreshToken()).isEqualTo(original.getRefreshToken());
			assertThat(firstResult.getAccessToken()).isNotEqualTo(secondResult.getAccessToken());
			assertThat(firstResult.getRefreshExpiresAt()).isEqualTo(secondResult.getRefreshExpiresAt())
				.isEqualTo(jwt.validateRefreshToken(original.getRefreshToken()).getExpiration().toInstant());
		} finally { executor.shutdownNow(); }
	}

	@Test
	@DisplayName("중개사 가입은 계정·동의·프로필을 저장하고 승인 대기 상태로 공통 인증을 사용한다")
	void agentSignupAndSharedAuthentication() throws Exception {
		String email = UUID.randomUUID() + "@example.com";
		String password = UUID.randomUUID().toString();
		var body = agentSignupBody(email, password);
		body.put("role", "ADMIN");
		body.put("approvalStatus", "APPROVED");
		MvcResult signup = mvc.perform(csrfPost("/api/v1/auth/signup/agent")
			.header("Authorization", "Bearer ignored-invalid-token")
			.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.approvalStatus").value("PENDING"))
			.andExpect(jsonPath("$.agentId").isNumber()).andExpect(jsonPath("$.documentDueAt").isString())
			.andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
		User user = users.findByEmail(email).orElseThrow();
		assertThat(user.getRole()).isEqualTo(Role.AGENT);
		assertThat(encoder.matches(password, user.getPasswordHash())).isTrue();
		assertThat(jdbc.queryForObject("select count(*) from user_consent where user_id = ?", Integer.class, user.getUserId())).isEqualTo(2);
		var agent = agents.findByUser_UserId(user.getUserId()).orElseThrow();
		assertThat(agent.getDocumentDueAt()).isAfter(java.time.LocalDateTime.now().plusDays(2));
		long agentId = mapper.readTree(signup.getResponse().getContentAsByteArray()).get("agentId").asLong();
		MvcResult login = mvc.perform(csrfPost("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
			.andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("AGENT"))
			.andExpect(jsonPath("$.user.agentId").value(agentId))
			.andExpect(jsonPath("$.user.approvalStatus").value("PENDING")).andReturn();
		String access = mapper.readTree(login.getResponse().getContentAsByteArray()).get("accessToken").asText();
		mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
			.andExpect(status().isOk()).andExpect(jsonPath("$.approvalStatus").value("PENDING"));
		mvc.perform(get("/api/v1/agents/me").param("userId", "999999").header("Authorization", "Bearer " + access))
			.andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(user.getUserId()))
			.andExpect(jsonPath("$.officeName").value("테스트 공인중개사"))
			.andExpect(jsonPath("$.representativeName").value("테스트 대표"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.user").doesNotExist());
		mvc.perform(get("/api/v1/agents/auth-check").header("Authorization", "Bearer " + access))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/auth-check").header("Authorization", "Bearer " + access))
			.andExpect(status().isForbidden());
		MvcResult refreshed = mvc.perform(csrfPost("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content(refreshBody(responseRefresh(login))))
			.andExpect(status().isOk()).andExpect(jsonPath("$.user.approvalStatus").value("PENDING")).andReturn();
		mvc.perform(csrfPost("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(responseRefresh(login))))
			.andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").value(responseRefresh(login)))
			.andExpect(jsonPath("$.user.approvalStatus").value("PENDING"));
		mvc.perform(csrfPost("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content(refreshBody(responseRefresh(refreshed))))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/agents/me").header("Authorization", "Bearer " + access)).andExpect(status().isOk());
		mvc.perform(csrfPost("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(responseRefresh(refreshed))))
			.andExpect(status().isOk()).andExpect(jsonPath("$.user.approvalStatus").value("PENDING"));
	}

	@Test
	@DisplayName("승인·반려 변경은 이미 발급한 토큰의 업무 접근과 인증 응답에 즉시 반영된다")
	void agentApprovalChangesAreLive() throws Exception {
		Account account = agentAccount();
		AuthResult session = login(account);
		var agent = agents.findByUser_UserId(account.user().getUserId()).orElseThrow();
		for (var approval : com.bogeum.common.enums.ApprovalStatus.values()) {
			org.springframework.test.util.ReflectionTestUtils.setField(agent, "approvalStatus", approval);
			org.springframework.test.util.ReflectionTestUtils.setField(agent, "rejectReason", approval.name().equals("REJECTED") ? "서류 보완 필요" : null);
			agents.saveAndFlush(agent);
			mvc.perform(get("/api/v1/agents/auth-check").header("Authorization", "Bearer " + session.getAccessToken()))
				.andExpect(status().is(approval.name().equals("APPROVED") ? 200 : 403));
			mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + session.getAccessToken()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.approvalStatus").value(approval.name()));
			mvc.perform(csrfPost("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content(refreshBody(session.getRefreshToken())))
				.andExpect(status().isOk()).andExpect(jsonPath("$.user.approvalStatus").value(approval.name()))
				.andExpect(jsonPath("$.refreshToken").value(session.getRefreshToken()));
		}
		mvc.perform(get("/api/v1/agents/me").header("Authorization", "Bearer " + session.getAccessToken()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.rejectReason").value("서류 보완 필요"));
		mvc.perform(csrfPost("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(account)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.user.approvalStatus").value("REJECTED"));
	}

	@Test
	@DisplayName("일반 회원과 비로그인 사용자는 중개사 프로필 및 업무 API에 접근할 수 없다")
	void agentRoleAndAnonymousAccess() throws Exception {
		AuthResult user = login(account(Role.USER, UserStatus.ACTIVE, true));
		for (String path : new String[]{"/api/v1/agents/me", "/api/v1/agents/auth-check", "/api/v1/agents/me/documents"}) {
			mvc.perform(get(path)).andExpect(status().isUnauthorized());
			mvc.perform(get(path).header("Authorization", "Bearer " + user.getAccessToken())).andExpect(status().isForbidden());
		}
		mvc.perform(post("/api/v1/auth/signup/agent").contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(agentSignupBody("csrf@example.com", "password"))))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	@Test
	@DisplayName("중개사 가입 이메일은 일반 회원 및 중개사 계정과 중복될 수 없다")
	void agentEmailUniqueness() throws Exception {
		for (Account existing : new Account[]{account(Role.USER, UserStatus.ACTIVE, true), agentAccount()}) {
			mvc.perform(csrfPost("/api/v1/auth/signup/agent").contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(agentSignupBody(existing.user().getEmail(), "password"))))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
		}
		Account agent = agentAccount();
		mvc.perform(csrfPost("/api/v1/auth/signup/user").contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(Map.of("email", agent.user().getEmail(), "password", "password", "name", "회원", "phone", "01000000000", "agreements", Map.of("privacyCollect", true, "thirdPartyProvide", true)))))
			.andExpect(status().isConflict());
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {"officeName", "officeAddress", "representativeName", "email", "phone", "password", "agreements", "longOffice", "longPassword"})
	void agentInvalidSignup(String field) throws Exception {
		String email = UUID.randomUUID() + "@example.com";
		var body = agentSignupBody(email, "password");
		if (field.equals("agreements")) body.put(field, Map.of("privacyCollect", false, "thirdPartyProvide", true));
		else if (field.equals("longOffice")) body.put("officeName", "a".repeat(101));
		else if (field.equals("longPassword")) body.put("password", "가".repeat(25));
		else body.put(field, "");
		mvc.perform(csrfPost("/api/v1/auth/signup/agent").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
			.andExpect(status().isBadRequest());
		assertThat(users.findByEmail(email)).isEmpty();
	}

	@Test
	@DisplayName("프로필 저장 실패 시 이미 저장한 계정과 필수 동의도 모두 롤백한다")
	void agentSignupRollsBackUserAndConsent() throws Exception {
		String email = UUID.randomUUID() + "@example.com";
		long before = jdbc.queryForObject("select count(*) from user_consent", Long.class);
		org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("test profile failure"))
			.when(agents).saveAndFlush(org.mockito.ArgumentMatchers.any(com.bogeum.domain.agent.model.Agent.class));
		mvc.perform(csrfPost("/api/v1/auth/signup/agent").contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(agentSignupBody(email, "password"))))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SIGNUP_CONFLICT"));
		assertThat(users.findByEmail(email)).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from user_consent", Long.class)).isEqualTo(before);
	}

	@Test
	@DisplayName("demo가 아닌 환경은 실습 승인 버튼을 숨기고 승인 API를 제공하지 않는다")
	void demoApprovalUnavailableOutsideDemo() throws Exception {
		var account = agentAccount();
		var session = login(account);
		mvc.perform(get("/api/v1/agents/me").header("Authorization", "Bearer " + session.getAccessToken()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.demoApprovalAvailable").value(false));
		mvc.perform(csrfPost("/api/v1/agents/me/demo-approval").header("Authorization", "Bearer " + session.getAccessToken()))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("마이페이지는 토큰 본인의 DB 정보만 조회하고 비밀번호를 노출하지 않는다")
	void myProfileUsesAuthenticatedUser() throws Exception {
		Account account = account(Role.USER, UserStatus.ACTIVE, true);
		AuthResult session = login(account);
		mvc.perform(get("/api/v1/users/me").param("userId", "999999")
				.header("Authorization", "Bearer " + session.getAccessToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId").value(account.user().getUserId()))
			.andExpect(jsonPath("$.email").value(account.user().getEmail()))
			.andExpect(jsonPath("$.name").value(account.user().getName()))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.phone").value("01000000000"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	@DisplayName("전화번호 수정은 본인에게만 적용되고 비밀번호와 다른 정보는 유지한다")
	void updateOnlyOwnPhone() throws Exception {
		Account account = account(Role.USER, UserStatus.ACTIVE, true);
		Account other = account(Role.USER, UserStatus.ACTIVE, true);
		AuthResult session = login(account);
		mvc.perform(csrfPatch("/api/v1/users/me").header("Authorization", "Bearer " + session.getAccessToken())
				.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
					"phone", " 010-9876-5432 ", "newPassword", "", "passwordConfirm", "",
					"userId", other.user().getUserId(), "role", "ADMIN", "email", "changed@example.com"))))
			.andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("010-9876-5432"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
		User saved = users.findById(account.user().getUserId()).orElseThrow();
		assertThat(saved.getPasswordHash()).isEqualTo(account.user().getPasswordHash());
		assertThat(saved.getEmail()).isEqualTo(account.user().getEmail());
		assertThat(saved.getRole()).isEqualTo(Role.USER);
		assertThat(saved.getAddress()).isEqualTo(account.user().getAddress());
		assertThat(users.findById(other.user().getUserId()).orElseThrow().getPhone()).isEqualTo("01000000000");
		mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + session.getAccessToken()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("010-9876-5432"));
	}

	@Test
	@DisplayName("새 비밀번호는 BCrypt로 저장되고 새 비밀번호로만 로그인된다")
	void updatePasswordHashAndLogin() throws Exception {
		Account account = account(Role.USER, UserStatus.ACTIVE, true);
		AuthResult session = login(account);
		String password = " new-" + UUID.randomUUID() + " ";
		MvcResult result = mvc.perform(csrfPatch("/api/v1/users/me")
				.header("Authorization", "Bearer " + session.getAccessToken()).contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of("currentPassword", account.password(),
					"newPassword", password, "passwordConfirm", password))))
			.andExpect(status().isOk()).andExpect(jsonPath("$.phone").value(account.user().getPhone())).andReturn();
		User saved = users.findById(account.user().getUserId()).orElseThrow();
		assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo(password).isNotEqualTo(account.user().getPasswordHash());
		assertThat(encoder.matches(password, saved.getPasswordHash())).isTrue();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(password, account.password(), saved.getPasswordHash());
		mvc.perform(csrfPost("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(account)))
			.andExpect(status().isUnauthorized());
		mvc.perform(csrfPost("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of("email", saved.getEmail(), "password", password))))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("현재 비밀번호가 없거나 틀리면 전화번호도 함께 저장하지 않는다")
	void passwordChangeRequiresCurrentPassword() throws Exception {
		Account account = account(Role.USER, UserStatus.ACTIVE, true);
		AuthResult session = login(account);
		for (String current : new String[]{"", "wrong", "가".repeat(25)}) {
			mvc.perform(csrfPatch("/api/v1/users/me").header("Authorization", "Bearer " + session.getAccessToken())
					.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
						"phone", "01099999999", "currentPassword", current, "newPassword", "new-password",
						"passwordConfirm", "new-password"))))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));
			User saved = users.findById(account.user().getUserId()).orElseThrow();
			assertThat(saved.getPhone()).isEqualTo(account.user().getPhone());
			assertThat(saved.getPasswordHash()).isEqualTo(account.user().getPasswordHash());
		}
	}

	@Test
	@DisplayName("불일치·공백·72바이트 초과 비밀번호와 잘못된 전화번호는 저장하지 않는다")
	void rejectsInvalidProfileChanges() throws Exception {
		Account account = account(Role.USER, UserStatus.ACTIVE, true);
		AuthResult session = login(account);
		for (Map<String, String> invalid : java.util.List.of(
				Map.of("newPassword", "new-password", "passwordConfirm", "different"),
				Map.of("newPassword", "   ", "passwordConfirm", "   "),
				Map.of("newPassword", "가".repeat(25), "passwordConfirm", "가".repeat(25)),
				Map.of("newPassword", "", "passwordConfirm", "new-password"),
				Map.of("phone", " "), Map.of("phone", "1".repeat(21)))) {
			var body = new java.util.HashMap<>(invalid);
			body.put("currentPassword", account.password());
			mvc.perform(csrfPatch("/api/v1/users/me").header("Authorization", "Bearer " + session.getAccessToken())
					.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
				.andExpect(status().isBadRequest());
		}
		User saved = users.findById(account.user().getUserId()).orElseThrow();
		assertThat(saved.getPhone()).isEqualTo(account.user().getPhone());
		assertThat(saved.getPasswordHash()).isEqualTo(account.user().getPasswordHash());
	}

	@Test
	@DisplayName("프로필 조회·수정에는 로그인이 필요하고 수정에는 CSRF도 필요하다")
	void profileRequiresAuthenticationAndCsrf() throws Exception {
		mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
		mvc.perform(csrfPatch("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
		AuthResult session = login(account(Role.USER, UserStatus.ACTIVE, true));
		mvc.perform(patch("/api/v1/users/me").header("Authorization", "Bearer " + session.getAccessToken())
				.contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"01099999999\"}"))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder csrfPatch(String path) throws Exception {
		MvcResult result = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
		var data = mapper.readTree(result.getResponse().getContentAsString());
		return patch(path).cookie(result.getResponse().getCookie("BOGEUM_CSRF"))
			.header(data.get("headerName").asText(), data.get("token").asText());
	}

	private java.util.HashMap<String, Object> agentSignupBody(String email, String password) {
		return new java.util.HashMap<>(Map.of("email", email, "password", password, "representativeName", "테스트 대표",
			"phone", "01000000000", "officeName", "테스트 공인중개사", "officeAddress", "서울시 테스트로 1",
			"agreements", Map.of("privacyCollect", true, "thirdPartyProvide", true)));
	}

	private Account agentAccount() throws Exception {
		String email = UUID.randomUUID() + "@example.com";
		String password = UUID.randomUUID().toString();
		mvc.perform(csrfPost("/api/v1/auth/signup/agent").contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(agentSignupBody(email, password)))).andExpect(status().isCreated());
		return new Account(users.findByEmail(email).orElseThrow(), password);
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder csrfPost(String path) throws Exception {
		MvcResult result = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
		var data = mapper.readTree(result.getResponse().getContentAsString());
		return post(path).cookie(result.getResponse().getCookie("BOGEUM_CSRF"))
			.header(data.get("headerName").asText(), data.get("token").asText());
	}
	private String refreshBody(String token) {
		return mapper.writeValueAsString(Map.of("refreshToken", token));
	}
	private String responseRefresh(MvcResult result) {
		return mapper.readTree(result.getResponse().getContentAsByteArray())
			.get("refreshToken").asText();
	}
	private Account account(Role role, UserStatus status, boolean hasPassword) {
		String password = UUID.randomUUID().toString();
		User user = users.save(User.builder().email(UUID.randomUUID() + "@example.com")
			.passwordHash(hasPassword ? encoder.encode(password) : null).name("테스트 회원")
			.phone("01000000000").role(role).userStatus(status).build());
		return new Account(user, password);
	}
	private AuthResult login(Account account) { return auth.login(account.user().getEmail(), account.password()); }
	private String credentials(Account account) {
		return mapper.writeValueAsString(Map.of("email", account.user().getEmail(), "password", account.password()));
	}
	private String expiredToken(AuthResult result, String type) {
		var claims = jwt.validateAccessToken(result.getAccessToken());
		return Jwts.builder().issuer("bogeum").subject(claims.getSubject())
			.claim("sid", claims.get("sid", String.class)).claim("tokenType", type)
			.issuedAt(Date.from(Instant.now().minusSeconds(60))).expiration(Date.from(Instant.now().minusSeconds(1)))
			.signWith(KEY, Jwts.SIG.HS256).compact();
	}
	private record Account(User user, String password) { }
	/** 관리자 권한 검증에만 사용하는 테스트 엔드포인트. */
	@org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
	@org.springframework.web.bind.annotation.RestController
	public static class AdminAuthCheckController {
		@org.springframework.web.bind.annotation.GetMapping("/api/v1/agents/auth-check")
		public Map<String, String> agentAuthCheck() { return Map.of("message", "승인된 중개사입니다."); }
		@org.springframework.web.bind.annotation.GetMapping("/api/v1/admin/auth-check")
		public Map<String, String> authCheck() {
			return Map.of("message", "관리자 인증에 성공했습니다.");
		}
	}}
