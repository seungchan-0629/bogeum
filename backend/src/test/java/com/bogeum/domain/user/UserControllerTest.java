package com.bogeum.domain.user;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

/** 실제 컨트롤러의 JSON 변환·검증·오류 처리를 DB 없이 확인한다. */
@WebMvcTest(UserController.class)
class UserControllerTest {

	private final MockMvc mockMvc;

	// 테스트 안에서만 서비스를 대체한다. 실제 실행 시에는 UserService가 DB를 조회한다.
	@MockitoBean
	private UserService userService;

	@Autowired
	UserControllerTest(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	@DisplayName("요청을 서비스에 전달하고 비밀번호 없이 정해진 네 필드만 반환한다")
	void loginReturnsPublicFieldsOnly() throws Exception {
		User user = User.builder().userId(1L).email("user@example.com")
			.name("홍길동").role(Role.TENANT).passwordHash("not-for-response")
			.lastLoginProvider(LoginProvider.LOCAL).build();
		when(userService.login("user@example.com", " password ")).thenReturn(user);

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email":"user@example.com","password":" password "}
				"""))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"userId":1,"name":"홍길동","role":"TENANT","lastLoginProvider":"LOCAL"}
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
