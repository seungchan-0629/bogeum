package com.bogeum.domain.openbanking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bogeum.common.enums.Role;
import com.bogeum.domain.openbanking.model.OpenBanking;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 실제 JWT 필터와 H2를 연결해 계좌 소유자 구분 및 응답 정보 범위를 검증한다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-account-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
	"spring.sql.init.mode=never", "auth.cookie-secure=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OpenBankingAccountIntegrationTest {
	private static final String ACCOUNT_PATH = "/openbanking/accounts/me";

	private final MockMvc mockMvc;
	private final UserRepository userRepository;
	private final OpenBankingRepository openBankingRepository;
	private final JwtUtil jwtUtil;
	@Autowired
	private com.bogeum.domain.user.PhoneVerificationRepository verifications;

	@Autowired
	OpenBankingAccountIntegrationTest(MockMvc mockMvc, UserRepository userRepository,
			OpenBankingRepository openBankingRepository, JwtUtil jwtUtil) {
		this.mockMvc = mockMvc;
		this.userRepository = userRepository;
		this.openBankingRepository = openBankingRepository;
		this.jwtUtil = jwtUtil;
	}

	@Test
	@DisplayName("다른 회원 번호를 보내도 본인 은행명과 마스킹된 계좌번호만 반환한다")
	void returnsOnlyOwnAccountDisplayInformation() throws Exception {
		User user = createUser();
		User otherUser = createUser();
		createAccount(user, "국민은행", "123-****-5678");
		OpenBanking otherAccount = createAccount(otherUser, "신한은행", "987-****-4321");

		mockMvc.perform(get(ACCOUNT_PATH).header("Authorization", bearerToken(user))
			.param("userId", otherUser.getUserId().toString())
			.param("openBankingId", otherAccount.getOpenBankingId().toString()))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"bankName":"국민은행","accountNumberMasked":"123-****-5678"}
				""", JsonCompareMode.STRICT));
		mockMvc.perform(get("/users/me").header("Authorization", bearerToken(user)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(user.getName()))
			.andExpect(jsonPath("$.email").value(user.getEmail()));
	}

	@Test
	@DisplayName("다른 회원의 계좌가 있어도 본인의 계좌가 없으면 204를 반환한다")
	void returnsNoContentWhenOwnAccountIsMissing() throws Exception {
		User user = createUser();
		createAccount(createUser(), "신한은행", "987-****-4321");

		mockMvc.perform(get(ACCOUNT_PATH).header("Authorization", bearerToken(user)))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));
		mockMvc.perform(get("/users/me").header("Authorization", bearerToken(user)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(user.getName()))
			.andExpect(jsonPath("$.email").value(user.getEmail()));
	}

	@Test
	@DisplayName("로그인하지 않으면 계좌 정보를 조회할 수 없다")
	void requiresLogin() throws Exception {
		mockMvc.perform(get(ACCOUNT_PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
	}

	@Test
	@DisplayName("프론트에서 사용하는 /auth 경로로 가입·로그인한 뒤 등록 계좌를 조회한다")
	void signupAndLoginThenReadAccount() throws Exception {
		var mapper = new JsonMapper();
		var csrfResponse = mockMvc.perform(get("/auth/csrf"))
			.andExpect(status().isOk()).andReturn().getResponse();
		var csrf = mapper.readTree(csrfResponse.getContentAsString());
		var csrfCookie = csrfResponse.getCookie("BOGEUM_CSRF");
		String headerName = csrf.get("headerName").asText();
		String csrfToken = csrf.get("token").asText();
		String email = UUID.randomUUID() + "@example.com";

		mockMvc.perform(post("/auth/signup/user").cookie(csrfCookie).header(headerName, csrfToken)
			.contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"%s","password":"test-password","name":"계좌 사용자","phone":"01012345678",
				 "verificationToken":"%s","agreements":{"privacyCollect":true,"thirdPartyProvide":true}}
				""".formatted(email, com.bogeum.PhoneVerificationFixtures.verifiedToken(verifications, "01012345678"))))
			.andExpect(status().isCreated());
		createAccount(userRepository.findByEmail(email).orElseThrow(), "국민은행", "123-****-5678");

		var loginResponse = mockMvc.perform(post("/auth/login").cookie(csrfCookie).header(headerName, csrfToken)
			.contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"%s","password":"test-password"}
				""".formatted(email)))
			.andExpect(status().isOk()).andReturn().getResponse();
		String accessToken = mapper.readTree(loginResponse.getContentAsString()).get("accessToken").asText();
		mockMvc.perform(get(ACCOUNT_PATH).header("Authorization", "Bearer " + accessToken))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"bankName":"국민은행","accountNumberMasked":"123-****-5678"}
				""", JsonCompareMode.STRICT));
	}

	private User createUser() {
		return userRepository.saveAndFlush(User.builder()
			.email(UUID.randomUUID() + "@example.com")
			.name("계좌 조회 사용자").phone("01012345678").role(Role.USER).build());
	}

	private OpenBanking createAccount(User user, String bankName, String accountNumberMasked) {
		return openBankingRepository.saveAndFlush(OpenBanking.builder()
			.user(user).userSeqNo("user-seq-" + user.getUserId())
			.fintechNum("fintech-" + user.getUserId())
			.bankName(bankName).accountNumberMasked(accountNumberMasked)
			.accessToken("expired-openbanking-access-token").refreshToken("openbanking-refresh-token")
			.tokenExpiresAt(LocalDateTime.now().minusDays(1)).build());
	}

	private String bearerToken(User user) {
		return "Bearer " + jwtUtil.createAccess(user.getUserId(), UUID.randomUUID().toString());
	}
}
