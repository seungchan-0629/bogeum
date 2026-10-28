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
import com.bogeum.domain.user.UserService.AuthResult;
import com.bogeum.domain.user.UserRepository.RefreshTokenRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.UserException;
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
	private final MockMvc mvc;
	private final UserRepository users;
	private final PasswordEncoder encoder;
	private final UserService auth;
	private final JwtUtil jwt;
	private final RefreshTokenRepository refreshTokens;
	private final JsonMapper mapper;

	@Autowired
	AuthIntegrationTests(MockMvc mvc, UserRepository users, PasswordEncoder encoder,
			UserService auth, JwtUtil jwt, RefreshTokenRepository refreshTokens, JsonMapper mapper) {
		this.mvc = mvc;
		this.users = users;
		this.encoder = encoder;
		this.auth = auth;
		this.jwt = jwt;
		this.refreshTokens = refreshTokens;
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
		mvc.perform(get("/api/v1/auth/me").param("userId", "999999").header("Authorization", "Bearer " + result.accessToken()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(account.user().getUserId()))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
		mvc.perform(get("/api/v1/admin/auth-check").header("Authorization", "Bearer " + result.accessToken()))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}

	@Test
	@DisplayName("ADMIN으로 인증한 요청은 관리자 API를 조회한다")
	void adminAccess() throws Exception {
		AuthResult result = login(account(Role.ADMIN, UserStatus.ACTIVE, true));
		mvc.perform(get("/api/v1/admin/auth-check").header("Authorization", "Bearer " + result.accessToken()))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("서명이 유효해도 로그인 기록이 없거나 다른 회원의 기록이면 인증을 거절한다")
	void rejectsMissingOrMismatchedSession() throws Exception {
		Account account = account(Role.USER, UserStatus.ACTIVE, true);
		Account other = account(Role.USER, UserStatus.ACTIVE, true);
		AuthResult result = login(account);
		String sessionKey = jwt.validateAccessToken(result.accessToken()).get("sid", String.class);
		for (String token : new String[]{
			jwt.createAccess(account.user().getUserId(), UUID.randomUUID().toString()),
			jwt.createAccess(other.user().getUserId(), sessionKey)}) {
			mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		}
	}

	@Test
	@DisplayName("로그인 이후 탈퇴한 회원의 기존 Access Token은 인증을 거절한다")
	void rejectsAccessAfterWithdrawal() throws Exception {
		Account account = account(Role.USER, UserStatus.ACTIVE, true);
		AuthResult result = login(account);
		User user = users.findById(account.user().getUserId()).orElseThrow();
		org.springframework.test.util.ReflectionTestUtils.setField(user, "userStatus", UserStatus.WITHDRAWN);
		users.saveAndFlush(user);
		mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + result.accessToken()))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
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
		String access = result.accessToken();
		int signature = access.lastIndexOf('.') + 1;
		String tampered = access.substring(0, signature) + (access.charAt(signature) == 'A' ? 'B' : 'A') + access.substring(signature + 1);
		for (String token : new String[]{tampered, result.refreshToken(), "bad-token"}) {
			mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCESS_TOKEN_INVALID"));
		}
	}

	@Test
	@DisplayName("갱신은 토큰을 회전하고 이전 Refresh Token 재사용을 거절한다")
	void rotateRefresh() throws Exception {
		AuthResult original = login(account(Role.USER, UserStatus.ACTIVE, true));
		MvcResult refreshed = mvc.perform(csrfPost("/api/v1/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON).content(refreshBody(original.refreshToken())))
			.andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString()).andReturn();
		assertThat(responseRefresh(refreshed)).isNotEqualTo(original.refreshToken());
		assertThat(jwt.validateRefreshToken(responseRefresh(refreshed)).getExpiration())
			.isEqualTo(jwt.validateRefreshToken(original.refreshToken()).getExpiration());
		mvc.perform(csrfPost("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(original.refreshToken())))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
		var claims = jwt.validateRefreshToken(original.refreshToken());
		var row = refreshTokens.findWithUser(claims.get("sid", String.class)).orElseThrow();
		assertThat(row.getTokenHash()).hasSize(64).isEqualTo(jwt.hashToken(responseRefresh(refreshed)));
	}

	@Test
	@DisplayName("Refresh JSON 누락·만료·Access 용도 토큰은 갱신에 사용할 수 없다")
	void invalidRefresh() throws Exception {
		AuthResult result = login(account(Role.USER, UserStatus.ACTIVE, true));
		mvc.perform(csrfPost("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
		for (String token : new String[]{expiredToken(result, "refresh"), result.accessToken()}) {
			mvc.perform(csrfPost("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(token)))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
		}
	}

	@Test
	@DisplayName("로그아웃 후에는 갱신과 기존 Access Token 요청이 모두 거절된다")
	void logoutRevokesSession() throws Exception {
		AuthResult result = login(account(Role.USER, UserStatus.ACTIVE, true));
		mvc.perform(csrfPost("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content(refreshBody(result.refreshToken())))
			.andExpect(status().isNoContent());
		mvc.perform(csrfPost("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(result.refreshToken())))
			.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + result.accessToken()))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
	}

	@Test
	@DisplayName("동일 Refresh Token을 동시에 보내도 정확히 한 요청만 갱신된다")
	void concurrentRefresh() throws Exception {
		AuthResult original = login(account(Role.USER, UserStatus.ACTIVE, true));
		var executor = Executors.newFixedThreadPool(2);
		var start = new CountDownLatch(1);
		try {
			java.util.concurrent.Callable<Boolean> task = () -> {
				start.await();
				try { auth.refresh(original.refreshToken()); return true; }
				catch (UserException expected) { return false; }
			};
			var first = executor.submit(task);
			var second = executor.submit(task);
			start.countDown();
			assertThat(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS)).isTrue();
		} finally { executor.shutdownNow(); }
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
		var claims = jwt.validateAccessToken(result.accessToken());
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
		@org.springframework.web.bind.annotation.GetMapping("/api/v1/admin/auth-check")
		public Map<String, String> authCheck() {
			return Map.of("message", "관리자 인증에 성공했습니다.");
		}
	}}
