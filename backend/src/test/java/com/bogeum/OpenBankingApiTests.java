package com.bogeum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bogeum.common.enums.Role;
import com.bogeum.common.enums.UserStatus;
import com.bogeum.domain.openbanking.service.OpenBankingService;
import com.bogeum.domain.openbanking.support.OpenBankingClient;
import com.bogeum.domain.settlement.repository.SettlementAccountRepository;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.JwtUtil;
import com.bogeum.utils.SecretCipher;

import tools.jackson.databind.json.JsonMapper;

/** 실제 Security 필터에서 인증·CSRF·요청 검증과 계좌 비밀값 제외를 확인한다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-openbanking-api-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
	"spring.sql.init.mode=never", "auth.cookie-secure=false",
	"openbanking.enabled=false",
	"openbanking.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenBankingApiTests {

	private static final String OPEN_BANKING = "/api/v1/tenants/me/open-banking";
	private static final String SETTLEMENT_ACCOUNT = "/api/v1/landlords/me/settlement-account";
	private final MockMvc mvc;
	private final UserRepository users;
	private final JwtUtil jwt;
	private final JsonMapper mapper;
	private final SettlementAccountRepository settlementAccounts;
	private final SecretCipher cipher;

	@MockitoBean
	private OpenBankingService openBanking;
	@MockitoBean
	private OpenBankingClient bankClient;

	@Autowired
	OpenBankingApiTests(MockMvc mvc, UserRepository users, JwtUtil jwt, JsonMapper mapper,
			SettlementAccountRepository settlementAccounts, SecretCipher cipher) {
		this.mvc = mvc;
		this.users = users;
		this.jwt = jwt;
		this.mapper = mapper;
		this.settlementAccounts = settlementAccounts;
		this.cipher = cipher;
	}

	@Test
	@DisplayName("비로그인 계좌 조회는 서비스 호출 전에 401로 거절한다")
	void accountLookupRequiresLogin() throws Exception {
		mvc.perform(get(OPEN_BANKING + "/accounts"))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		verifyNoInteractions(openBanking, bankClient);
	}

	@Test
	@DisplayName("유효한 JWT가 있어도 CSRF 없는 인증 시작은 403이다")
	void authorizationRequiresCsrf() throws Exception {
		mvc.perform(post(OPEN_BANKING + "/authorization").header("Authorization", bearer(account())))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		verifyNoInteractions(openBanking, bankClient);
	}

	@Test
	@DisplayName("JWT와 CSRF를 검증한 회원 번호로 인증을 시작하고 인증 주소만 반환한다")
	void authorizationUsesAuthenticatedUser() throws Exception {
		User user = account();
		String url = "https://testapi.openbanking.or.kr/oauth/2.0/authorize?state=opaque";
		when(openBanking.beginAuthorization(user.getUserId())).thenReturn(url);
		mvc.perform(csrfPost(OPEN_BANKING + "/authorization").header("Authorization", bearer(user)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.authorizationUrl").value(url))
			.andExpect(jsonPath("$.accessToken").doesNotExist())
			.andExpect(jsonPath("$.clientSecret").doesNotExist());
		verify(openBanking).beginAuthorization(user.getUserId());
		verifyNoInteractions(bankClient);
	}

	@Test
	@DisplayName("빈 인증 코드와 state는 은행 토큰 교환 전에 400으로 거절한다")
	void callbackRequiresCodeAndState() throws Exception {
		User user = account();
		for (String body : new String[] {"{\"code\":\"\",\"state\":\"valid-state\"}",
			"{\"code\":\"valid-code\",\"state\":\" \"}"}) {
			mvc.perform(csrfPost(OPEN_BANKING + "/callback").header("Authorization", bearer(user))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
		verifyNoInteractions(openBanking, bankClient);
	}

	@Test
	@DisplayName("정산 계좌 원문은 암호화해 저장하고 등록·조회 응답에는 마스킹한 계좌만 반환한다")
	void settlementAccountKeepsSecretValuesOutOfResponses() throws Exception {
		User landlord = account();
		String accountNumber = "12345678901234";
		String body = "{\"bankCode\":\"097\",\"accountNumber\":\"" + accountNumber
			+ "\",\"holderName\":\"임대인\"}";
		var registered = mvc.perform(csrfPost(SETTLEMENT_ACCOUNT).header("Authorization", bearer(landlord))
			.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk()).andExpect(jsonPath("$.accountNumberMasked").value("***********234"))
			.andExpect(jsonPath("$.accountNumber").doesNotExist())
			.andExpect(jsonPath("$.accountNumberEncrypted").doesNotExist()).andReturn();
		var stored = settlementAccounts.findByLandlordUserId(landlord.getUserId()).orElseThrow();
		String encrypted = stored.getAccountNumberEncrypted();
		assertNotEquals(accountNumber, encrypted);
		assertEquals(accountNumber, cipher.decrypt(encrypted));
		assertFalse(registered.getResponse().getContentAsString().contains(accountNumber));
		assertFalse(registered.getResponse().getContentAsString().contains(encrypted));
		var retrieved = mvc.perform(get(SETTLEMENT_ACCOUNT).header("Authorization", bearer(landlord)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.accountNumberMasked").value("***********234"))
			.andExpect(jsonPath("$.accountNumber").doesNotExist())
			.andExpect(jsonPath("$.accountNumberEncrypted").doesNotExist()).andReturn();
		assertFalse(retrieved.getResponse().getContentAsString().contains(accountNumber));
		assertFalse(retrieved.getResponse().getContentAsString().contains(encrypted));
		mvc.perform(get(SETTLEMENT_ACCOUNT).header("Authorization", bearer(account())))
			.andExpect(status().isNoContent());
		verifyNoInteractions(openBanking, bankClient);
	}

	private User account() {
		return users.save(User.builder().email(UUID.randomUUID() + "@example.com").name("임대인")
			.phone("01012345678").role(Role.USER).userStatus(UserStatus.ACTIVE).build());
	}

	private String bearer(User user) {
		return "Bearer " + jwt.createAccess(user.getUserId(), "openbanking-api-test");
	}

	private MockHttpServletRequestBuilder csrfPost(String path) throws Exception {
		var result = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
		var csrf = mapper.readTree(result.getResponse().getContentAsString());
		return post(path).cookie(result.getResponse().getCookie("BOGEUM_CSRF"))
			.header(csrf.get("headerName").asText(), csrf.get("token").asText());
	}
}
