package com.bogeum.domain.openbanking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bogeum.config.OpenBankingProperties;
import com.bogeum.domain.openbanking.support.OpenBankingClient;

import tools.jackson.databind.json.JsonMapper;

/** 실제 금융기관 호출 없이 송수신 전문과 미확정 응답 분류를 검증한다. */
class OpenBankingClientTest {

	private static final String TRAN_ID = "M123456789U123456789";
	private static final String FINTECH_NUM = "123456789012345678901234";
	private MockRestServiceServer server;
	private OpenBankingClient client;

	@BeforeEach
	void setUp() {
		var builder = RestClient.builder().baseUrl("https://testapi.openbanking.or.kr");
		server = MockRestServiceServer.bindTo(builder).build();
		var properties = new OpenBankingProperties(true, "https://testapi.openbanking.or.kr",
			"client-id", "client-secret", "http://localhost:5173/mypage/payment-method", "M123456789",
			"12345678901234", "a".repeat(128), "", Duration.ofSeconds(10));
		client = new OpenBankingClient(builder.build(), properties);
	}

	@Test
	void authorizationRequestsAllScopesAndEncodesState() {
		String url = client.authorizationUrl("state/+");
		assertTrue(url.contains("scope=login%20inquiry%20transfer"));
		assertTrue(url.contains("state=state%2F%2B"));
		assertTrue(url.contains("auth_type=0"));
		assertTrue(client.newBankTranId().matches("M123456789U[A-Z0-9]{9}"));
	}

	@Test
	void codeExchangeUsesFormAndDoesNotExposeTokenInToString() {
		server.expect(requestTo("https://testapi.openbanking.or.kr/oauth/2.0/token"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(request -> {
				assertTrue(request.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED));
				String form = ((MockClientHttpRequest) request).getBodyAsString();
				assertTrue(form.contains("client_secret=client-secret"));
				assertTrue(form.contains("grant_type=authorization_code"));
				assertTrue(form.contains("code=one-time-code"));
			})
			.andRespond(withSuccess("""
				{"token_type":"Bearer","access_token":"private-token","refresh_token":"private-refresh",
				 "scope":"login inquiry transfer","user_seq_no":"1234567890","expires_in":3600}
				""", MediaType.APPLICATION_JSON));
		var token = client.exchangeCode("one-time-code");
		assertEquals("private-token", token.accessToken());
		assertFalse(token.toString().contains("private-token"));
		server.verify();
	}

	@Test
	void withdrawalSendsCompanyAccountAndValidatesBankResult() {
		server.expect(requestTo("https://testapi.openbanking.or.kr/v2.0/transfer/withdraw/fin_num"))
			.andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer tenant-token"))
			.andExpect(request -> {
				var body = new JsonMapper().readTree(((MockClientHttpRequest) request).getBodyAsString());
				assertEquals("N", body.path("cntr_account_type").asText());
				assertEquals("12345678901234", body.path("cntr_account_num").asText());
				assertEquals(TRAN_ID, body.path("bank_tran_id").asText());
				assertEquals(FINTECH_NUM, body.path("fintech_use_num").asText());
				assertEquals("650000", body.path("tran_amt").asText());
				assertTrue(body.path("tran_dtime").asText().matches("[0-9]{14}"));
			})
			.andRespond(withSuccess(withdrawalResponse("A0000", "000", TRAN_ID, "650000"), MediaType.APPLICATION_JSON));
		assertTrue(withdraw().succeeded());
		server.verify();
	}

	@Test
	void apiErrorAndMismatchedTransactionAreUncertain() {
		server.expect(requestTo("https://testapi.openbanking.or.kr/v2.0/transfer/withdraw/fin_num"))
			.andRespond(withSuccess(withdrawalResponse("A9999", "000", TRAN_ID, "650000"), MediaType.APPLICATION_JSON));
		assertThrows(OpenBankingException.class, this::withdraw);
		server.verify();
		server.reset();
		server.expect(requestTo("https://testapi.openbanking.or.kr/v2.0/transfer/withdraw/fin_num"))
			.andRespond(withSuccess(withdrawalResponse("A0000", "000", "M000000000U000000000", "650000"), MediaType.APPLICATION_JSON));
		assertThrows(OpenBankingException.class, this::withdraw);
		server.verify();
	}

	@Test
	void depositChecksNestedBankResponseAndKeepsNameVerification() {
		server.expect(requestTo("https://testapi.openbanking.or.kr/v2.0/transfer/deposit/acnt_num"))
			.andExpect(header("Authorization", "Bearer institution-token"))
			.andExpect(request -> {
				var body = new JsonMapper().readTree(((MockClientHttpRequest) request).getBodyAsString());
				assertEquals("on", body.path("name_check_option").asText());
				assertEquals("1", body.path("req_cnt").asText());
				assertEquals(1, body.path("req_list").size());
				assertEquals("097", body.path("req_list").get(0).path("bank_code_std").asText());
				assertEquals("1234567890", body.path("req_list").get(0).path("account_num").asText());
			})
			.andRespond(withSuccess("""
				{"rsp_code":"A0000","res_cnt":"1","res_list":[
				 {"tran_no":"1","bank_tran_id":"%s","bank_rsp_code":"101"}]}
				""".formatted(TRAN_ID), MediaType.APPLICATION_JSON));
		var result = deposit();
		assertFalse(result.succeeded());
		assertEquals("101", result.code());
		server.verify();
	}

	@Test
	void emptyDepositResultCannotBeSuccess() {
		server.expect(requestTo("https://testapi.openbanking.or.kr/v2.0/transfer/deposit/acnt_num"))
			.andRespond(withSuccess("{\"rsp_code\":\"A0000\",\"res_cnt\":\"1\",\"res_list\":[]}", MediaType.APPLICATION_JSON));
		assertThrows(OpenBankingException.class, this::deposit);
		server.verify();
	}

	@Test
	void transportFailureCannotBecomeAConfirmedTransferFailure() {
		server.expect(requestTo("https://testapi.openbanking.or.kr/v2.0/transfer/withdraw/fin_num"))
			.andRespond(withException(new IOException("연결 끊김")));
		assertThrows(OpenBankingException.class, this::withdraw);
		server.verify();
	}

	@Test
	void accountsRequireBankSuppliedWithdrawalConsent() {
		server.expect(requestTo("https://testapi.openbanking.or.kr/v2.0/user/me?user_seq_no=1234567890"))
			.andRespond(withSuccess("""
				{"rsp_code":"A0000","user_seq_no":"1234567890","res_list":[
				 {"fintech_use_num":"%s","bank_code_std":"097","account_num_masked":"123-***",
				  "account_holder_name":"홍길동","transfer_agree_yn":"Y"},
				 {"fintech_use_num":"%s","bank_code_std":"097","account_num_masked":"456-***",
				  "account_holder_name":"홍길동"}]}
				""".formatted(FINTECH_NUM, "123456789012345678901235"), MediaType.APPLICATION_JSON));
		var accounts = client.accounts("tenant-token", "1234567890");
		assertTrue(accounts.get(0).withdrawalAgreed());
		assertFalse(accounts.get(1).withdrawalAgreed());
		server.verify();
	}

	private OpenBankingClient.TransferResult withdraw() {
		return client.withdraw("tenant-token", TRAN_ID, FINTECH_NUM, new BigDecimal("650000"), "홍길동", "1");
	}

	private OpenBankingClient.TransferResult deposit() {
		return client.deposit("institution-token", TRAN_ID, "097", "1234567890", "임대인",
			new BigDecimal("650000"), "홍길동", "1", FINTECH_NUM);
	}

	private String withdrawalResponse(String apiCode, String bankCode, String transactionId, String amount) {
		return """
			{"rsp_code":"%s","bank_rsp_code":"%s","bank_tran_id":"%s","tran_amt":"%s","fintech_use_num":"%s"}
			""".formatted(apiCode, bankCode, transactionId, amount, FINTECH_NUM);
	}
}
