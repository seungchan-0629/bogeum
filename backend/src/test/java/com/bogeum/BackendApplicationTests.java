package com.bogeum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bogeum.common.enums.LoginProvider;
import com.bogeum.common.enums.Role;
import com.bogeum.common.enums.UserStatus;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.user.UserException;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;

import jakarta.validation.ConstraintViolationException;

/** 운영 DB와 분리된 H2에서 가입 저장부터 기존 로그인 API까지 검증한다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-auth-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.jpa.show-sql=false",
	"spring.sql.init.mode=never", "auth.cookie-secure=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BackendApplicationTests {

	private final UserService userService;
	private final PasswordEncoder passwordEncoder;
	private final JdbcTemplate jdbcTemplate;
	private final MockMvc mockMvc;

	@MockitoSpyBean
	private UserRepository userRepository;

	@Autowired
	BackendApplicationTests(UserService userService, PasswordEncoder passwordEncoder,
			JdbcTemplate jdbcTemplate, MockMvc mockMvc) {
		this.userService = userService;
		this.passwordEncoder = passwordEncoder;
		this.jdbcTemplate = jdbcTemplate;
		this.mockMvc = mockMvc;
	}

	@BeforeEach
	void clearTestDatabase() {
		jdbcTemplate.update("DELETE FROM refresh_token");
		jdbcTemplate.update("DELETE FROM user_consent");
		jdbcTemplate.update("DELETE FROM user");
	}

	@Test
	void contextLoads() {
	}

	@Test
	@DisplayName("계정·필수 동의를 저장하고 기존 로그인 API로 로그인한다")
	void signupThenLogin() throws Exception {
		String signupRequest = """
			{"email":"signup@example.com","password":" password ","name":"가입자","phone":"01012345678",
			 "agreements":{"privacyCollect":true,"thirdPartyProvide":true}}
			""";
		mockMvc.perform(csrfPost("/api/v1/auth/signup/user").contentType(MediaType.APPLICATION_JSON)
			.content(signupRequest))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.userId").isNumber())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
		User user = userRepository.findByEmail("signup@example.com").orElseThrow();
		User stored = userRepository.findById(user.getUserId()).orElseThrow();
		assertEquals("가입자", stored.getName());
		assertEquals("01012345678", stored.getPhone());
		assertEquals(Role.USER, stored.getRole());
		assertNotEquals(" password ", stored.getPasswordHash());
		assertTrue(passwordEncoder.matches(" password ", stored.getPasswordHash()));
		assertEquals(UserStatus.ACTIVE, stored.getUserStatus());
		assertNull(stored.getIdentityVerifiedAt());
		assertNull(stored.getLastLoginProvider());
		assertEquals(Set.of("PRIVACY_COLLECTION", "THIRD_PARTY_PROVISION"),
			Set.copyOf(jdbcTemplate.queryForList("SELECT consent_type FROM user_consent WHERE user_id = ?",
				String.class, user.getUserId())));
		assertEquals(2L, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM user_consent WHERE agreed_at IS NOT NULL", Long.class));
		mockMvc.perform(csrfPost("/api/v1/auth/signup/user").contentType(MediaType.APPLICATION_JSON)
			.content(signupRequest))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
		assertDatabaseCounts(1, 2);

		mockMvc.perform(csrfPost("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email":"signup@example.com","password":" password "}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.userId").value(user.getUserId()))
			.andExpect(jsonPath("$.user.name").value("가입자"))
			.andExpect(jsonPath("$.user.role").value("USER"))
			.andExpect(jsonPath("$.user.lastLoginProvider").value("LOCAL"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
		assertEquals(LoginProvider.LOCAL, userRepository.findById(user.getUserId()).orElseThrow().getLastLoginProvider());

		mockMvc.perform(csrfPost("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email":"signup@example.com","password":"wrong-password"}
				"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
	}

	@Test
	@DisplayName("이미 등록된 이메일은 계정과 동의를 추가 저장하지 않는다")
	void rejectsDuplicateEmail() {
		signup();
		UserException exception = assertThrows(UserException.class, this::signup);
		assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
		assertDatabaseCounts(1, 2);
	}

	@Test
	@DisplayName("중복 사전 조회를 통과해도 DB 고유 제약으로 중복 저장을 막는다")
	void databaseRejectsDuplicateEmail() {
		signup();
		// 동시에 가입하는 경우처럼 사전 조회에서 기존 계정을 발견하지 못한 상황이다.
		doReturn(Optional.empty()).when(userRepository).findByEmail("signup@example.com");
		UserException exception = assertThrows(UserException.class, this::signup);
		assertEquals(ErrorCode.SIGNUP_CONFLICT, exception.getErrorCode());
		assertDatabaseCounts(1, 2);
	}

	@ParameterizedTest
	@ValueSource(strings = {"ADMIN", "AGENT", "LANDLORD", "TENANT"})
	@DisplayName("가입 요청에 역할을 임의로 추가해도 일반 사용자로만 저장한다")
	void signupDoesNotAcceptAccountRole(String role) throws Exception {
		mockMvc.perform(csrfPost("/api/v1/auth/signup/user").contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email":"signup@example.com","password":"password","name":"가입자","phone":"01012345678",
				 "role":"%s","agreements":{"privacyCollect":true,"thirdPartyProvide":true}}
				""".formatted(role)))
			.andExpect(status().isCreated());
		assertEquals(Role.USER, userRepository.findByEmail("signup@example.com").orElseThrow().getRole());
	}

	@Test
	@DisplayName("두 필수 동의 중 하나라도 없으면 저장하지 않는다")
	void requiresBothConsents() {
		for (boolean[] agreements : new boolean[][] {{false, true}, {true, false}, {false, false}}) {
			UserException exception = assertThrows(UserException.class, () -> userService.signup(
				"signup@example.com", "password", "가입자", "01012345678",
				agreements[0], agreements[1]));
			assertEquals(ErrorCode.INVALID_SIGNUP, exception.getErrorCode());
		}
		assertDatabaseCounts(0, 0);
	}

	@Test
	@DisplayName("필수값·이메일·DB 길이를 검증하고 누락된 정보를 임의로 채우지 않는다")
	void requiresValidUserInformation() {
		String[][] invalidInputs = {
			{null, "password", "가입자", "01012345678"},
			{"invalid-email", "password", "가입자", "01012345678"},
			{"signup@example.com", null, "가입자", "01012345678"},
			{"signup@example.com", "   ", "가입자", "01012345678"},
			{"signup@example.com", "password", "", "01012345678"},
			{"signup@example.com", "password", "가입자", null},
			{"signup@example.com", "password", "가".repeat(51), "01012345678"},
			{"signup@example.com", "password", "가입자", "1".repeat(21)}
		};
		for (String[] input : invalidInputs) {
			assertThrows(ConstraintViolationException.class, () -> userService.signup(
				input[0], input[1], input[2], input[3], true, true));
		}
		assertDatabaseCounts(0, 0);
	}

	@ParameterizedTest
	@ValueSource(strings = {"a", "가"})
	@DisplayName("비밀번호의 UTF-8 72바이트 경계는 허용하고 초과하면 가입·로그인을 거절한다")
	void checksPasswordByteLimit(String character) throws Exception {
		String password = character.repeat(character.equals("a") ? 72 : 24);
		userService.signup("signup@example.com", password, "가입자", "01012345678", true, true);
		mockMvc.perform(csrfPost("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"signup@example.com\",\"password\":\"" + password + "\"}"))
			.andExpect(status().isOk());
		String oversizedPassword = password + character;
		UserException exception = assertThrows(UserException.class, () -> userService.signup(
			"other@example.com", oversizedPassword, "가입자", "01012345678", true, true));
		assertEquals(ErrorCode.INVALID_SIGNUP, exception.getErrorCode());
		mockMvc.perform(csrfPost("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"signup@example.com\",\"password\":\"" + oversizedPassword + "\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
		assertDatabaseCounts(1, 2);
	}

	@Test
	@DisplayName("두 번째 동의 저장이 실패하면 사용자와 첫 번째 동의도 롤백한다")
	void rollsBackEntireSignupWhenConsentFails() {
		jdbcTemplate.execute("ALTER TABLE user_consent ADD CONSTRAINT test_reject_consent "
			+ "CHECK (consent_type <> 'THIRD_PARTY_PROVISION')");
		try {
			UserException exception = assertThrows(UserException.class, this::signup);
			assertEquals(ErrorCode.SIGNUP_CONFLICT, exception.getErrorCode());
			assertNotNull(exception.getCause());
			assertDatabaseCounts(0, 0);
		} finally {
			jdbcTemplate.execute("ALTER TABLE user_consent DROP CONSTRAINT test_reject_consent");
		}
	}


	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder csrfPost(String path) throws Exception {
		var result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/csrf"))
			.andExpect(status().isOk()).andReturn();
		var data = new tools.jackson.databind.json.JsonMapper().readTree(result.getResponse().getContentAsString());
		return post(path).cookie(result.getResponse().getCookie("BOGEUM_CSRF"))
			.header(data.get("headerName").asText(), data.get("token").asText());
	}

	private User signup() {
		return userService.signup("signup@example.com", "password", "가입자", "01012345678",
			true, true);
	}

	private void assertDatabaseCounts(long users, long consents) {
		assertEquals(users, userRepository.count());
		assertEquals(consents, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_consent", Long.class));
	}
}
