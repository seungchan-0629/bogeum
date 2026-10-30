package com.bogeum.domain.openbanking.support;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.bogeum.config.OpenBankingProperties;
import com.bogeum.domain.openbanking.OpenBankingException;

import tools.jackson.databind.JsonNode;

/** 금융결제원 공식 규격: https://developers.kftc.or.kr/dev/openapi/open-banking */
public class OpenBankingClient {

	private static final DateTimeFormatter TRAN_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
	private static final char[] TRAN_CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
	private static final SecureRandom RANDOM = new SecureRandom();

	private final RestClient restClient;
	private final OpenBankingProperties properties;

	public OpenBankingClient(RestClient restClient, OpenBankingProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	public String authorizationUrl(String state) {
		requireConfigured();
		requireText(properties.redirectUri());
		requireText(state);
		return UriComponentsBuilder.fromUriString(properties.baseUrl()).path("/oauth/2.0/authorize")
			.queryParam("response_type", "code").queryParam("client_id", properties.clientId())
			.queryParam("redirect_uri", properties.redirectUri()).queryParam("scope", "login inquiry transfer")
			.queryParam("state", "{state}").queryParam("auth_type", "0").encode().buildAndExpand(state).toUriString();
	}

	public Token exchangeCode(String code) {
		requireConfigured();
		requireText(properties.redirectUri());
		var form = credentials("authorization_code");
		form.add("code", code);
		form.add("redirect_uri", properties.redirectUri());
		return token(form, Set.of("login", "inquiry", "transfer"));
	}

	public Token refreshToken(String refreshToken) {
		requireConfigured();
		var form = credentials("refresh_token");
		form.add("refresh_token", refreshToken);
		form.add("scope", "login inquiry transfer");
		return token(form, Set.of());
	}

	public Token issueInstitutionToken() {
		requireConfigured();
		var form = credentials("client_credentials");
		form.add("scope", "oob");
		return token(form, Set.of("oob"));
	}

	public List<Account> accounts(String accessToken, String userSeqNo) {
		requireConfigured();
		JsonNode response;
		try {
			response = restClient.get().uri(uri -> uri.path("/v2.0/user/me")
				.queryParam("user_seq_no", userSeqNo).build())
				.headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(JsonNode.class);
		} catch (RestClientException exception) {
			throw OpenBankingException.unavailable();
		}
		requireApiSuccess(response);
		if (!userSeqNo.equals(text(response, "user_seq_no")) || !response.path("res_list").isArray()) {
			throw OpenBankingException.invalidResponse();
		}
		List<Account> accounts = new ArrayList<>();
		for (JsonNode account : response.path("res_list")) {
			String fintechUseNum = text(account, "fintech_use_num");
			String bankCode = text(account, "bank_code_std");
			String masked = text(account, "account_num_masked");
			String holderName = text(account, "account_holder_name");
			if (!fintechUseNum.matches("[0-9]{24}") || !bankCode.matches("[0-9]{3}")
					|| masked.isBlank() || holderName.isBlank()) {
				throw OpenBankingException.invalidResponse();
			}
			accounts.add(new Account(fintechUseNum, bankCode, masked, holderName,
				"Y".equals(text(account, "transfer_agree_yn"))));
		}
		return List.copyOf(accounts);
	}

	/** 임차인 계좌에서 회사 약정 계좌로 수납한다. */
	public TransferResult withdraw(String accessToken, String bankTranId, String fintechUseNum,
			BigDecimal amount, String tenantName, String tenantNum) {
		requireTransferConfigured();
		var body = transferBody();
		body.put("bank_tran_id", bankTranId);
		body.put("dps_print_content", "보금월세수납");
		body.put("wd_print_content", "보금월세");
		body.put("fintech_use_num", fintechUseNum);
		body.put("tran_amt", amount(amount));
		body.put("req_client_name", tenantName);
		body.put("req_client_num", tenantNum);
		body.put("req_client_fintech_use_num", fintechUseNum);
		body.put("transfer_purpose", "TR");
		var response = postJson("/v2.0/transfer/withdraw/fin_num", accessToken, body);
		var result = transferResult(response, bankTranId, amount);
		if (result.succeeded() && !fintechUseNum.equals(text(response, "fintech_use_num"))) {
			throw OpenBankingException.invalidResponse();
		}
		return result;
	}

	/** 회사 약정 계좌에 수납된 월세를 임대인의 실계좌로 지급한다. */
	public TransferResult deposit(String accessToken, String bankTranId, String bankCode,
			String accountNumber, String holderName, BigDecimal amount, String tenantName,
			String tenantNum, String tenantFintechUseNum) {
		requireTransferConfigured();
		if (properties.depositPassPhrase() == null || !properties.depositPassPhrase().matches("[a-fA-F0-9]{128}")) {
			throw OpenBankingException.unavailable();
		}
		var transfer = new LinkedHashMap<String, Object>();
		transfer.put("tran_no", "1");
		transfer.put("bank_tran_id", bankTranId);
		transfer.put("bank_code_std", bankCode);
		transfer.put("account_num", accountNumber);
		transfer.put("account_holder_name", holderName);
		transfer.put("print_content", "보금월세지급");
		transfer.put("tran_amt", amount(amount));
		transfer.put("req_client_name", tenantName);
		transfer.put("req_client_num", tenantNum);
		transfer.put("req_client_fintech_use_num", tenantFintechUseNum);
		transfer.put("transfer_purpose", "TR");
		var body = transferBody();
		body.put("wd_pass_phrase", properties.depositPassPhrase());
		body.put("wd_print_content", "보금월세지급");
		body.put("name_check_option", "on");
		body.put("req_cnt", "1");
		body.put("req_list", List.of(transfer));
		var response = postJson("/v2.0/transfer/deposit/acnt_num", accessToken, body);
		requireApiSuccess(response);
		JsonNode results = response.path("res_list");
		if (!"1".equals(text(response, "res_cnt")) || !results.isArray() || results.size() != 1
				|| !"1".equals(text(results.get(0), "tran_no"))) {
			throw OpenBankingException.invalidResponse();
		}
		var result = transferResult(results.get(0), bankTranId, amount);
		if (result.succeeded() && (!bankCode.equals(text(results.get(0), "bank_code_std"))
				|| !accountNumber.equals(text(results.get(0), "account_num")))) {
			throw OpenBankingException.invalidResponse();
		}
		return result;
	}

	public String newBankTranId() {
		requireConfigured();
		if (properties.fintechOrgCode() == null || !properties.fintechOrgCode().matches("[A-Za-z0-9]{10}")) {
			throw OpenBankingException.unavailable();
		}
		StringBuilder suffix = new StringBuilder(9);
		for (int index = 0; index < 9; index++) {
			suffix.append(TRAN_CHARACTERS[RANDOM.nextInt(TRAN_CHARACTERS.length)]);
		}
		return properties.fintechOrgCode() + "U" + suffix;
	}

	private Token token(MultiValueMap<String, String> form, Set<String> requiredScopes) {
		JsonNode response;
		try {
			response = restClient.post().uri("/oauth/2.0/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form).retrieve().body(JsonNode.class);
		} catch (RestClientException exception) {
			throw OpenBankingException.unavailable();
		}
		if (response == null || !"bearer".equalsIgnoreCase(text(response, "token_type"))
				|| text(response, "access_token").isBlank() || response.path("expires_in").asLong(0) <= 0) {
			throw OpenBankingException.invalidResponse();
		}
		String scope = text(response, "scope");
		if (!List.of(scope.split("\\s+")).containsAll(requiredScopes)) {
			throw OpenBankingException.invalidResponse();
		}
		return new Token(text(response, "access_token"), text(response, "refresh_token"),
			text(response, "user_seq_no"), scope, response.path("expires_in").asLong());
	}

	private MultiValueMap<String, String> credentials(String grantType) {
		var form = new LinkedMultiValueMap<String, String>();
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());
		form.add("grant_type", grantType);
		return form;
	}

	private Map<String, Object> transferBody() {
		var body = new LinkedHashMap<String, Object>();
		body.put("cntr_account_type", "N");
		body.put("cntr_account_num", properties.contractAccountNum());
		body.put("tran_dtime", ZonedDateTime.now(KOREA).format(TRAN_TIME));
		return body;
	}

	private JsonNode postJson(String path, String accessToken, Object body) {
		try {
			JsonNode response = restClient.post().uri(path).headers(headers -> headers.setBearerAuth(accessToken))
				.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
			requireApiSuccess(response);
			return response;
		} catch (RestClientException exception) {
			// HTTP 오류·타임아웃은 실제 이체 여부가 불명확하므로 재이체 가능한 실패로 반환하지 않는다.
			throw OpenBankingException.unavailable();
		}
	}

	private TransferResult transferResult(JsonNode response, String bankTranId, BigDecimal amount) {
		String bankCode = text(response, "bank_rsp_code");
		if (!bankTranId.equals(text(response, "bank_tran_id")) || !bankCode.matches("[0-9]{3}")) {
			throw OpenBankingException.invalidResponse();
		}
		if ("000".equals(bankCode)) {
			try {
				if (new BigDecimal(text(response, "tran_amt")).compareTo(amount) != 0) {
					throw OpenBankingException.invalidResponse();
				}
			} catch (NumberFormatException exception) {
				throw OpenBankingException.invalidResponse();
			}
		}
		return new TransferResult("000".equals(bankCode), bankCode);
	}

	private void requireConfigured() {
		if (!properties.enabled()) {
			throw OpenBankingException.unavailable();
		}
		requireText(properties.baseUrl());
		requireText(properties.clientId());
		requireText(properties.clientSecret());
	}

	private void requireTransferConfigured() {
		requireConfigured();
		if (properties.contractAccountNum() == null || !properties.contractAccountNum().matches("[0-9]{1,16}")) {
			throw OpenBankingException.unavailable();
		}
	}

	private void requireText(String value) {
		if (value == null || value.isBlank()) {
			throw OpenBankingException.unavailable();
		}
	}

	private void requireApiSuccess(JsonNode response) {
		if (response == null || !"A0000".equals(text(response, "rsp_code"))) {
			throw OpenBankingException.invalidResponse();
		}
	}

	private String text(JsonNode node, String field) {
		return node.path(field).asText("");
	}

	private String amount(BigDecimal amount) {
		if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0
				|| amount.compareTo(new BigDecimal("999999999999")) > 0) {
			throw OpenBankingException.invalidResponse();
		}
		return amount.toBigIntegerExact().toString();
	}

	public record Token(String accessToken, String refreshToken, String userSeqNo, String scope, long expiresIn) {
		@Override
		public String toString() {
			return "Token[비밀값 생략]";
		}
	}

	public record Account(String fintechUseNum, String bankCode, String accountNumberMasked,
			String holderName, boolean withdrawalAgreed) {
	}

	public record TransferResult(boolean succeeded, String code) {
	}
}
