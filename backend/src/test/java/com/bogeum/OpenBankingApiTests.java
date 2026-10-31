package com.bogeum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doThrow;
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
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.openbanking.OpenBankingException;
import com.bogeum.domain.openbanking.OpenBankingService;
import com.bogeum.domain.openbanking.OpenBankingClient;
import com.bogeum.domain.settlement.SettlementRepository.SettlementAccountRepository;
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
	private static final String ADMIN_RENT = "/api/v1/admin/rents/999999";
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
			+ "\",\"holderName\":\"임대인\",\"birthDate\":\"900101\"}";
		var registered = mvc.perform(csrfPost(SETTLEMENT_ACCOUNT).header("Authorization", bearer(landlord))
			.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk()).andExpect(jsonPath("$.accountNumberMasked").value("***********234"))
			.andExpect(jsonPath("$.accountNumber").doesNotExist())
			.andExpect(jsonPath("$.birthDate").doesNotExist())
			.andExpect(jsonPath("$.accountNumberEncrypted").doesNotExist()).andReturn();
		var stored = settlementAccounts.findByLandlordUserId(landlord.getUserId()).orElseThrow();
		String encrypted = stored.getAccountNumberEncrypted();
		assertNotNull(stored.getVerifiedAt());
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
		verify(bankClient).verifyAccount("097", accountNumber, "임대인", "900101");
		verifyNoInteractions(openBanking);
	}

	@Test
	@DisplayName("정산계좌 등록은 생년월일 필수 입력을 검증하고 잘못된 요청을 은행에 보내지 않는다")
	void settlementAccountRequiresBirthDate() throws Exception {
		User landlord = account();
		for (String birthDateField : new String[] {"", ",\"birthDate\":null", ",\"birthDate\":\"\"",
			",\"birthDate\":\"19900101\"", ",\"birthDate\":\"900230\""}) {
			String body = "{\"bankCode\":\"097\",\"accountNumber\":\"12345678901234\",\"holderName\":\"임대인\""
				+ birthDateField + "}";
			mvc.perform(csrfPost(SETTLEMENT_ACCOUNT).header("Authorization", bearer(landlord))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
		}
		verifyNoInteractions(openBanking, bankClient);
	}

	@Test
	@DisplayName("은행 예금주 검증을 통과하지 못한 정산계좌는 등록하지 않는다")
	void bankRejectedSettlementAccountIsNotStored() throws Exception {
		User landlord = account();
		doThrow(OpenBankingException.of(ErrorCode.INVALID_SETTLEMENT_ACCOUNT)).when(bankClient)
			.verifyAccount("097", "12345678901234", "임대인", "900101");
		mvc.perform(csrfPost(SETTLEMENT_ACCOUNT).header("Authorization", bearer(landlord))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"bankCode\":\"097\",\"accountNumber\":\"12345678901234\",\"holderName\":\"임대인\",\"birthDate\":\"900101\"}"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SETTLEMENT_ACCOUNT"));
		assertFalse(settlementAccounts.findByLandlordUserId(landlord.getUserId()).isPresent());
		verifyNoInteractions(openBanking);
	}

	@Test
	@DisplayName("일반 회원은 관리자 이체 조회와 증빙 확정 API에 접근할 수 없다")
	void transferOperationsRequireAdminRole() throws Exception {
		User user = account();
		mvc.perform(get(ADMIN_RENT + "/transfer-status").header("Authorization", bearer(user)))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		for (String endpoint : new String[] {"/collection-resolution", "/settlement-resolution"}) {
			mvc.perform(csrfPost(ADMIN_RENT + endpoint).header("Authorization", bearer(user))
				.contentType(MediaType.APPLICATION_JSON).content(validResolution()))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		}
		verifyNoInteractions(openBanking, bankClient);
	}

	@Test
	@DisplayName("관리자의 증빙 확정 요청도 CSRF가 없으면 거절한다")
	void adminResolutionRequiresCsrf() throws Exception {
		User admin = account(Role.ADMIN);
		for (String endpoint : new String[] {"/collection-resolution", "/settlement-resolution"}) {
			mvc.perform(post(ADMIN_RENT + endpoint).header("Authorization", bearer(admin))
				.contentType(MediaType.APPLICATION_JSON).content(validResolution()))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		}
		verifyNoInteractions(openBanking, bankClient);
	}

	@Test
	@DisplayName("관리자 증빙 확정은 거래번호·성공여부·증빙 참조를 모두 검증한다")
	void adminResolutionValidatesBodyBeforeTransferAccess() throws Exception {
		User admin = account(Role.ADMIN);
		String[] invalidBodies = {
			"{}", "{",
			"{\"bankTranId\":\"short\",\"succeeded\":true,\"evidenceReference\":\"BANK-001\"}",
			"{\"bankTranId\":\"T000000001U000000001\",\"evidenceReference\":\"BANK-001\"}",
			"{\"bankTranId\":\"T000000001U000000001\",\"succeeded\":null,\"evidenceReference\":\"BANK-001\"}",
			"{\"bankTranId\":\"T000000001U000000001\",\"succeeded\":true,\"evidenceReference\":\" \"}",
			"{\"bankTranId\":\"T000000001U000000001\",\"succeeded\":true,\"evidenceReference\":\"" + "x".repeat(201) + "\"}"
		};
		for (String endpoint : new String[] {"/collection-resolution", "/settlement-resolution"}) {
			for (String body : invalidBodies) {
				mvc.perform(csrfPost(ADMIN_RENT + endpoint).header("Authorization", bearer(admin))
					.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
			}
		}
		verifyNoInteractions(openBanking, bankClient);
	}

	@Test
	@DisplayName("관리자는 이체 상태를 조회할 수 있고 없는 납부건은 공통 404로 반환한다")
	void adminCanAccessTransferStatus() throws Exception {
		mvc.perform(get(ADMIN_RENT + "/transfer-status").header("Authorization", bearer(account(Role.ADMIN))))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RENT_PAYMENT_NOT_FOUND"));
		verifyNoInteractions(openBanking, bankClient);
	}

	private String validResolution() {
		return "{\"bankTranId\":\"T000000001U000000001\",\"succeeded\":true,\"evidenceReference\":\"BANK-001\"}";
	}

	private User account() {
		return account(Role.USER);
	}

	private User account(Role role) {
		return users.save(User.builder().email(UUID.randomUUID() + "@example.com").name("임대인")
			.phone("01012345678").role(role).userStatus(UserStatus.ACTIVE).build());
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
