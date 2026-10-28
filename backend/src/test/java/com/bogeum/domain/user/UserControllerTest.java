package com.bogeum.domain.user;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import com.bogeum.domain.user.UserService.AuthResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import com.bogeum.common.enums.LoginProvider;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.user.model.User;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 실제 컨트롤러의 JSON 변환·검증·오류 처리를 DB 없이 확인한다. */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

	private static final String SIGNUP_REQUEST = """
		{"email":"user@example.com","password":" password ","name":"가입자","phone":"01012345678",
		 "agreements":{"privacyCollect":true,"thirdPartyProvide":true}}
		""";

	private final MockMvc mockMvc;

	// 테스트 안에서만 서비스를 대체한다. 실제 실행 시에는 UserService가 DB를 조회한다.
	@MockitoBean
	private UserService userService;
	@Autowired
	UserControllerTest(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	@DisplayName("회원가입은 201과 사용자 식별자만 반환하며 입력 비밀번호를 그대로 전달한다")
	void signupReturnsCreatedUserIdOnly() throws Exception {
		when(userService.signup("user@example.com", " password ", "가입자", "01012345678",
			true, true)).thenReturn(User.builder().userId(1L).passwordHash("secret").build());

		mockMvc.perform(post("/api/v1/auth/signup/user").contentType(MediaType.APPLICATION_JSON)
			.content(SIGNUP_REQUEST))
			.andExpect(status().isCreated())
			.andExpect(content().json("{\"userId\":1}", JsonCompareMode.STRICT));
		verify(userService).signup("user@example.com", " password ", "가입자", "01012345678",
			true, true);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{}", "{", "{\"email\":\"invalid\"}", "{\"email\":null}",
		"{\"password\":\"   \"}", "{\"password\":null}", "{\"name\":\"\"}", "{\"phone\":null}",
		"{\"agreements\":null}",
		"{\"agreements\":{}}", "{\"agreements\":{\"privacyCollect\":true}}",
		"{\"agreements\":{\"privacyCollect\":false,\"thirdPartyProvide\":true}}",
		"{\"agreements\":{\"privacyCollect\":true,\"thirdPartyProvide\":false}}",
		"{\"agreements\":{\"privacyCollect\":null,\"thirdPartyProvide\":true}}"
	})
	@DisplayName("잘못된 가입 정보와 필수 동의 누락은 서비스 호출 전에 거절한다")
	void rejectsInvalidSignupRequest(String overrides) throws Exception {
		String request = overrides;
		if (!overrides.equals("{}") && !overrides.equals("{")) {
			JsonMapper mapper = new JsonMapper();
			ObjectNode fields = (ObjectNode) mapper.readTree(SIGNUP_REQUEST);
			fields.setAll((ObjectNode) mapper.readTree(overrides));
			request = mapper.writeValueAsString(fields);
		}
		mockMvc.perform(post("/api/v1/auth/signup/user").contentType(MediaType.APPLICATION_JSON)
			.content(request))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		verifyNoInteractions(userService);
	}

	@Test
	@DisplayName("가입 서비스의 중복·가입 조건·저장 충돌 오류를 공통 응답으로 반환한다")
	void signupErrorsUseCommonResponse() throws Exception {
		UserException[] exceptions = {
			UserException.emailAlreadyExists(), UserException.invalidSignup(),
			UserException.signupConflict(new IllegalStateException("internal-detail"))
		};
		for (UserException exception : exceptions) {
			doThrow(exception).when(userService).signup("user@example.com", " password ", "가입자", "01012345678",
				true, true);
			mockMvc.perform(post("/api/v1/auth/signup/user").contentType(MediaType.APPLICATION_JSON)
				.content(SIGNUP_REQUEST))
				.andExpect(status().is(exception.getErrorCode().getHttpStatus().value()))
				.andExpect(jsonPath("$.code").value(exception.getErrorCode().name()))
				.andExpect(jsonPath("$.message").value(exception.getErrorCode().getMessage()))
				.andExpect(jsonPath("$.cause").doesNotExist());
		}
	}

	@Test
	@DisplayName("요청을 서비스에 전달하고 회원 정보와 두 토큰 및 만료 시각을 반환한다")
	void loginReturnsPublicFieldsOnly() throws Exception {
		User user = User.builder().userId(1L).email("user@example.com")
			.name("홍길동").role(Role.TENANT).passwordHash("not-for-response")
			.lastLoginProvider(LoginProvider.LOCAL).build();
		when(userService.login("user@example.com", " password ")).thenReturn(new AuthResult(user, "test-access", "test-refresh", Instant.parse("2030-01-01T00:00:00Z")));

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email":"user@example.com","password":" password "}
				"""))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"user":{"userId":1,"name":"홍길동","role":"TENANT","lastLoginProvider":"LOCAL","contractRoles":[]},"accessToken":"test-access","refreshToken":"test-refresh","refreshExpiresAt":"2030-01-01T00:00:00Z"}
				""", JsonCompareMode.STRICT));
		verify(userService).login("user@example.com", " password ");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{}",
		"{\"email\":\"\",\"password\":\"password\"}",
		"{\"email\":\"invalid-email\",\"password\":\"password\"}",
		"{\"email\":\"user@example.com\",\"password\":\"\"}",
		"{\"email\":\"user@example.com\",\"password\":\"   \"}",
		"{\"email\":\"user@example.com\",\"password\":null}",
		"{\"loginId\":\"user@example.com\",\"password\":\"password\"}",
		"{"
	})
	@DisplayName("잘못된 요청은 서비스 호출 없이 400과 공통 오류를 반환한다")
	void rejectsInvalidRequest(String request) throws Exception {
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(request))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
				{"code":"INVALID_REQUEST","message":"요청값을 확인해 주세요."}
				""", JsonCompareMode.STRICT));
		verifyNoInteractions(userService);
	}

	@Test
	@DisplayName("인증 실패를 계정 정보 노출 없이 401로 변환한다")
	void loginFailureReturnsUnauthorized() throws Exception {
		when(userService.login("user@example.com", "wrong-password")).thenThrow(UserException.loginFailed());
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email":"user@example.com","password":"wrong-password"}
				"""))
			.andExpect(status().isUnauthorized())
			.andExpect(content().json("""
				{"code":"LOGIN_FAILED","message":"이메일 또는 비밀번호를 확인해 주세요."}
				""", JsonCompareMode.STRICT));
	}
}
