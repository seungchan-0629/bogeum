package com.bogeum.domain.openbanking;

import com.bogeum.domain.openbanking.FakeKFTC.StateStore;
import com.bogeum.domain.openbanking.model.OpenBanking;
import com.bogeum.domain.openbanking.model.OpenBankingDTO.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/*
(계좌등록, 유저 토큰발급), 기업oob토큰 발급
등록계좌조회 ,이체결과조회 , 토큰 리프래시, 송금, 출금
*/
@Service
@Validated
@RequiredArgsConstructor
public class OpenBankingService
{
	private static final String REDIRECT_URI = "http://localhost:5173/mypage/openbanking/callback";

    private final OpenBankingRepository openBankingRepository;
    private final OpenBankingTransferRepository openBankingTransferRepository;
    private final StateStore stateStore;
	// 가상 금결원은 HTTP/1.1만 지원하므로 HTTP/2 업그레이드 협상을 하지 않는다.
	private final RestClient restClient = RestClient.builder()
		.baseUrl("http://localhost:8090")
		.requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1).build()))
		.build();

	public String createAuthorizationUrl(Long userId) {
		String state = UUID.randomUUID().toString().replace("-", "");
		stateStore.save(state, userId);
		return UriComponentsBuilder.fromUriString("http://localhost:8090/oauth/2.0/authorize")
			.queryParam("response_type", "code")
			.queryParam("client_id", "bogeum")
			.queryParam("redirect_uri", REDIRECT_URI)
			.queryParam("scope", "login inquiry transfer")
			.queryParam("state", state)
			.queryParam("auth_type", "0")
			.build().encode().toUriString();
	}


    //계좌 등록
    @Transactional
    public OpenBanking registerAccount(String code, String state)
    {
        Long userId = stateStore.consume(state);

        // 3. 인증 코드 전송 준비
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", "bogeum");
        form.add("client_secret", "mock-secret");
        form.add("redirect_uri", REDIRECT_URI);

        // 4. 인증 코드로 토큰 발급 요청
        JsonNode tokenJson = restClient.post()
                .uri("/oauth/2.0/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        if (tokenJson == null) {
            throw new IllegalStateException("토큰 발급 실패");
        }

        // 5. 토큰 정보 추출
        String accessToken = tokenJson.path("access_token").asText();
        String refreshToken = tokenJson.path("refresh_token").asText();
        String userSeqNo = tokenJson.path("user_seq_no").asText();
        long expiresIn = tokenJson.path("expires_in").asLong();

        if (accessToken.isBlank() || refreshToken.isBlank()
                || userSeqNo.isBlank() || expiresIn <= 0) {
            throw new IllegalStateException("토큰 응답 정보 누락");
        }

        // 6. 토큰을 이용해 계좌 정보 조회
        JsonNode accountJson = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2.0/user/me")
                        .queryParam("user_seq_no", userSeqNo)
                        .build())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(JsonNode.class);

        if (accountJson == null ||
                !"A0000".equals(accountJson.path("rsp_code").asText())) {
            throw new IllegalStateException("계좌 정보 조회 실패");
        }

        // 7. 첫 번째 계좌 정보 추출
        JsonNode accounts = accountJson.path("res_list");

        if (!accounts.isArray() || accounts.isEmpty()) {
            throw new IllegalStateException("등록된 계좌가 없습니다.");
        }

        String fintechUseNum = accounts.get(0)
                .path("fintech_use_num")
                .asText();

        if (fintechUseNum.isBlank()) {
            throw new IllegalStateException("핀테크이용번호 누락");
        }

        // 8. 재등록은 기존 계좌 ID를 유지해 계좌 정보와 토큰을 갱신한다.
		Long openBankingId = openBankingRepository.findByUserId(userId)
			.map(OpenBanking::getOpenBankingId).orElse(null);
        OpenBanking openBanking = OpenBanking.builder()
			.openBankingId(openBankingId)
                .userId(userId)
                .userSeqNo(userSeqNo)
                .fintechNum(fintechUseNum)
				.bankName(accounts.get(0).path("bank_name").asText(""))
				.accountNumberMasked(accounts.get(0).path("account_num_masked").asText(""))
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn))
                .build();

        // 9. DB 저장
        return openBankingRepository.saveAndFlush(openBanking);
    }





	/** 마이페이지에 표시할 본인의 등록 계좌를 DB에서 조회한다. */
	@Transactional(readOnly = true)
	public Optional<OpenBanking> findAccountByUserId(Long userId) {
		return openBankingRepository.findByUserId(userId);
	}

    // 등록계좌조회: DB 계좌, 조회조건, 갱신에 필요한 앱 인증정보와 scope를 받는다.
    public RegisteredAccountRes getRegisteredAccounts(OpenBanking account, RegisteredAccountReq request,
                                                      TokenRefreshReq tokenRequest) {
        if (!Objects.equals(account.getUserSeqNo(), request.getUserSeqNo())) {
            throw new IllegalArgumentException("계좌와 조회할 사용자일련번호가 다릅니다.");
        }
        JsonNode response = withUserToken(account, tokenRequest, token -> restClient.get()
                .uri(builder -> builder.path("/v2.0/account/list")
                        .queryParam("user_seq_no", request.getUserSeqNo())
                        .queryParam("include_cancel_yn", request.getIncludeCancelYn())
                        .queryParam("sort_order", request.getSortOrder()).build())
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve().body(JsonNode.class));
        List<RegisteredAccountItemRes> accounts = new ArrayList<>();
        for (JsonNode item : responseList(response)) {
            accounts.add(RegisteredAccountItemRes.builder()
                    .fintechUseNum(item.path("fintech_use_num").asText(""))
					.bankName(item.path("bank_name").asText(""))
					.accountNumberMasked(item.path("account_num_masked").asText(""))
                    .transferAgreeYn(item.path("transfer_agree_yn").asText(""))
                    .accountState(item.path("account_state").asText("")).build());
        }
        return RegisteredAccountRes.builder()
                .apiTranId(response.path("api_tran_id").asText(""))
                .apiTranDtm(response.path("api_tran_dtm").asText(""))
                .rspCode(response.path("rsp_code").asText(""))
                .rspMessage(response.path("rsp_message").asText(""))
                .userName(response.path("user_name").asText("")).resList(accounts).build();
    }

    // 출금: 사용자 계좌에서 이용기관 약정계좌로 출금한다. 사용자 transfer 토큰을 사용한다.
    public WithdrawRes withdraw(OpenBanking account, WithdrawReq request, TokenRefreshReq tokenRequest) {
        if (!Objects.equals(account.getFintechNum(), request.getFintechUseNum())) {
            throw new IllegalArgumentException("계좌와 출금할 핀테크이용번호가 다릅니다.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bank_tran_id", request.getBankTranId());
        body.put("cntr_account_type", request.getCntrAccountType());
        body.put("cntr_account_num", request.getCntrAccountNum());
        body.put("dps_print_content", request.getDpsPrintContent());
        body.put("fintech_use_num", request.getFintechUseNum());
        body.put("tran_amt", request.getTranAmt());
        body.put("tran_dtime", request.getTranDtime());
        body.put("req_client_name", request.getReqClientName());
        body.put("req_client_num", request.getReqClientNum());
        body.put("transfer_purpose", request.getTransferPurpose());
        // 기존 DTO에 없는 요청고객 계좌정보는 DB의 출금계좌 번호를 사용한다.
        body.put("req_client_fintech_use_num", account.getFintechNum());
        JsonNode response = withUserToken(account, tokenRequest,
                token -> postJson("/v2.0/transfer/withdraw/fin_num", token, body));
        return WithdrawRes.builder()
                .apiTranId(response.path("api_tran_id").asText(""))
                .apiTranDtm(response.path("api_tran_dtm").asText(""))
                .rspCode(response.path("rsp_code").asText(""))
                .rspMessage(response.path("rsp_message").asText(""))
                .bankTranId(response.path("bank_tran_id").asText(""))
                .bankTranDate(response.path("bank_tran_date").asText(""))
                .bankRspCode(response.path("bank_rsp_code").asText(""))
                .bankRspMessage(response.path("bank_rsp_message").asText(""))
                .tranAmt(response.path("tran_amt").asText("")).build();
    }

    // 송금: 기관 약정계좌에서 수취계좌로 입금한다. 기관 OOB 토큰을 받는다.
    // reqClientFintechUseNum은 DTO의 수취계좌와 다른 '송금인'의 핀테크이용번호다.
    public RemittanceRes remit(String institutionAccessToken, RemittanceReq request,
                               String reqClientFintechUseNum) {
        requireText(reqClientFintechUseNum, "송금인 핀테크이용번호");
        requireCount(request.getReqCnt(), request.getReqList(), 1);
        RemittanceItemReq item = request.getReqList().get(0);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("tran_no", item.getTranNo());
        entry.put("bank_tran_id", item.getBankTranId());
        entry.put("fintech_use_num", item.getFintechUseNum());
        entry.put("print_content", item.getPrintContent());
        entry.put("tran_amt", item.getTranAmt());
        entry.put("req_client_name", item.getReqClientName());
        entry.put("req_client_num", item.getReqClientNum());
        entry.put("req_client_fintech_use_num", reqClientFintechUseNum);
        entry.put("transfer_purpose", item.getTransferPurpose());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cntr_account_type", request.getCntrAccountType());
        body.put("cntr_account_num", request.getCntrAccountNum());
        body.put("wd_pass_phrase", request.getWdPassPhrase());
        body.put("wd_print_content", request.getWdPrintContent());
        body.put("name_check_option", request.getNameCheckOption());
        body.put("tran_dtime", request.getTranDtime());
        body.put("req_cnt", request.getReqCnt());
        body.put("req_list", List.of(entry));
        JsonNode response = checkResponse(
                postJson("/v2.0/transfer/deposit/fin_num", institutionAccessToken, body), false);
        List<RemittanceItemRes> results = new ArrayList<>();
        for (JsonNode result : responseList(response)) {
            results.add(RemittanceItemRes.builder()
                    .bankTranId(result.path("bank_tran_id").asText(""))
                    .bankTranDate(result.path("bank_tran_date").asText(""))
                    .bankCodeTran(result.path("bank_code_tran").asText(""))
                    .bankRspCode(result.path("bank_rsp_code").asText(""))
                    .bankRspMessage(result.path("bank_rsp_message").asText("")).build());
        }
        return RemittanceRes.builder()
                .apiTranId(response.path("api_tran_id").asText(""))
                .apiTranDtm(response.path("api_tran_dtm").asText(""))
                .rspCode(response.path("rsp_code").asText(""))
                .rspMessage(response.path("rsp_message").asText("")).resList(results).build();
    }

    // 이체결과조회: 출금/입금 모두 기관 OOB 토큰을 사용하며 최대 25건까지 조회한다.
    public TransferResultRes getTransferResult(String institutionAccessToken, TransferResultReq request) {
        requireCount(request.getReqCnt(), request.getReqList(), 25);
        List<Map<String, String>> entries = new ArrayList<>();
        for (TransferResultItemReq item : request.getReqList()) {
            entries.add(Map.of("tran_no", item.getTranNo(),
                    "org_bank_tran_id", item.getOrgBankTranId(),
                    "org_bank_tran_date", item.getOrgBankTranDate(),
                    "org_tran_amt", item.getOrgTranAmt()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("check_type", request.getCheckType());
        body.put("tran_dtime", request.getTranDtime());
        body.put("req_cnt", request.getReqCnt());
        body.put("req_list", entries);
        JsonNode response = checkResponse(postJson("/v2.0/transfer/result", institutionAccessToken, body), false);
        List<TransferResultItemRes> results = new ArrayList<>();
        for (JsonNode item : responseList(response)) {
            results.add(TransferResultItemRes.builder()
                    .tranNo(item.path("tran_no").asText(""))
                    .bankTranId(item.path("bank_tran_id").asText(""))
                    .bankTranDate(item.path("bank_tran_date").asText(""))
                    .bankCodeTran(item.path("bank_code_tran").asText(""))
                    .bankRspCode(item.path("bank_rsp_code").asText(""))
                    .bankRspMessage(item.path("bank_rsp_message").asText(""))
                    .tranAmt(item.path("tran_amt").asText("")).build());
        }
        return TransferResultRes.builder()
                .apiTranId(response.path("api_tran_id").asText(""))
                .apiTranDtm(response.path("api_tran_dtm").asText(""))
                .rspCode(response.path("rsp_code").asText(""))
                .rspMessage(response.path("rsp_message").asText(""))
                .resCnt(response.path("res_cnt").asText("")).resList(results).build();
    }

    // request에서는 clientId/clientSecret/scope를 받고, refresh token은 DB 계좌 값을 사용한다.
    // scope는 최초 토큰에 부여된 권한 범위를 그대로 전달한다.
    public UserTokenRes refreshToken(OpenBanking account, TokenRefreshReq request) {
        if (account == null || account.getOpenBankingId() == null) {
            throw new IllegalArgumentException("DB에서 조회한 등록계좌가 필요합니다.");
        }
        requireText(account.getRefreshToken(), "DB 리프레시 토큰");
        Objects.requireNonNull(request, "토큰 갱신 요청이 필요합니다.");
        requireText(request.getScope(), "기존 토큰의 scope");
        MultiValueMap<String, String> form = tokenForm(request.getClientId(), request.getClientSecret());
        form.add("refresh_token", account.getRefreshToken());
        form.add("scope", request.getScope());
        form.add("grant_type", "refresh_token");
        LocalDateTime requestedAt = LocalDateTime.now();
        JsonNode response = postToken(form);
        String refreshToken = response.path("refresh_token").asText("");
        if (refreshToken.isBlank()) refreshToken = account.getRefreshToken();
        String userSeqNo = response.path("user_seq_no").asText("");
        if (userSeqNo.isBlank()) userSeqNo = account.getUserSeqNo();
        if (!Objects.equals(userSeqNo, account.getUserSeqNo())) {
            throw new OpenBankingException("갱신된 토큰의 사용자일련번호가 기존 계좌와 다릅니다.");
        }
        String scope = response.path("scope").asText("");
        if (scope.isBlank()) scope = request.getScope();
        // 기존 ID와 계좌정보를 유지해 새 토큰만 DB에 반영한다. 전달받은 엔티티는 변경하지 않는다.
        openBankingRepository.saveAndFlush(OpenBanking.builder()
                .openBankingId(account.getOpenBankingId()).userId(account.getUserId())
                .userSeqNo(account.getUserSeqNo()).fintechNum(account.getFintechNum())
				.bankName(account.getBankName()).accountNumberMasked(account.getAccountNumberMasked())
                .accessToken(response.path("access_token").asText("")).refreshToken(refreshToken)
                .tokenExpiresAt(requestedAt.plusSeconds(response.path("expires_in").asLong())).build());
        return UserTokenRes.builder()
                .accessToken(response.path("access_token").asText(""))
                .tokenType(response.path("token_type").asText(""))
                .expiresIn(response.path("expires_in").asLong())
                .refreshToken(refreshToken).scope(scope).userSeqNo(userSeqNo).build();
    }

    // OOB 발급·재발급. refresh token이 없으며 보관과 유효기간 관리는 호출자가 한다.
    // clientId/clientSecret을 사용하고 scope와 grant_type은 명세의 고정값으로 보낸다.
    public InstitutionTokenRes issueInstitutionToken(InstitutionTokenReq request) {
        Objects.requireNonNull(request, "기관 토큰 요청이 필요합니다.");
        MultiValueMap<String, String> form = tokenForm(request.getClientId(), request.getClientSecret());
        form.add("scope", "oob");
        form.add("grant_type", "client_credentials");
        JsonNode response = postToken(form);
        if (!List.of(response.path("scope").asText("").split("\\s+")).contains("oob")) {
            throw new OpenBankingException("기관용 OOB 권한이 없는 토큰입니다.");
        }
        if (response.path("client_use_code").asText("").isBlank()) {
            throw new OpenBankingException("기관 토큰 응답의 이용기관코드가 없습니다.");
        }
        return InstitutionTokenRes.builder()
                .accessToken(response.path("access_token").asText(""))
                .tokenType(response.path("token_type").asText(""))
                .expiresIn(response.path("expires_in").asLong())
                .scope(response.path("scope").asText(""))
                .clientUseCode(response.path("client_use_code").asText("")).build();
    }

    // 토큰만 꺼내는 공통 메서드. 만료되면 갱신·DB 저장 후 새 토큰을 반환한다.
    private String getValidAccessToken(OpenBanking account, TokenRefreshReq request) {
        Objects.requireNonNull(account, "DB에서 조회한 등록계좌가 필요합니다.");
        if (account.getAccessToken() != null && !account.getAccessToken().isBlank()
                && account.getTokenExpiresAt() != null
                && account.getTokenExpiresAt().isAfter(LocalDateTime.now())) {
            return account.getAccessToken();
        }
        return refreshToken(account, request).getAccessToken();
    }

    private JsonNode withUserToken(OpenBanking account, TokenRefreshReq tokenRequest,
                                   Function<String, JsonNode> request) {
        String token = getValidAccessToken(account, tokenRequest);
        JsonNode response = request.apply(token);
        // DB상 유효해도 서버가 만료를 명시하면 한 번 갱신하고 재요청한다.
        // 방금 갱신한 토큰이 거절되면 오래된 refresh token으로 다시 갱신하지 않는다.
        if (response != null && "O0003".equals(response.path("rsp_code").asText(""))
                && Objects.equals(token, account.getAccessToken())) {
            response = request.apply(refreshToken(account, tokenRequest).getAccessToken());
        }
        return checkResponse(response, false);
    }

    private JsonNode postJson(String path, String token, Object body) {
        requireText(token, "액세스 토큰");
        return restClient.post().uri(path)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(JsonNode.class);
    }

    private MultiValueMap<String, String> tokenForm(String clientId, String clientSecret) {
        requireText(clientId, "Client ID");
        requireText(clientSecret, "Client Secret");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return form;
    }

    private JsonNode postToken(MultiValueMap<String, String> form) {
        JsonNode response = checkResponse(restClient.post().uri("/oauth/2.0/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(JsonNode.class), true);
        if (response.path("access_token").asText("").isBlank()
                || !"Bearer".equalsIgnoreCase(response.path("token_type").asText(""))
                || response.path("expires_in").asLong() <= 0) {
            throw new OpenBankingException("토큰 응답의 필수 정보가 누락되거나 잘못되었습니다.");
        }
        return response;
    }

    private JsonNode checkResponse(JsonNode response, boolean tokenResponse) {
        if (response == null || !response.isObject()) {
            throw new OpenBankingException("금결원 응답이 비어 있거나 형식이 잘못되었습니다.");
        }
        if (!response.path("error").asText("").isBlank()) {
            throw new OpenBankingException("금결원 토큰 요청이 거절되었습니다.");
        }
        String code = response.path("rsp_code").asText("");
        if ((!tokenResponse || !code.isBlank()) && !"A0000".equals(code)) {
            throw new OpenBankingException("금결원 API 요청 실패: " + code);
        }
        return response;
    }

    private JsonNode responseList(JsonNode response) {
        JsonNode list = response.path("res_list");
        if (!list.isArray()) {
            throw new OpenBankingException("금결원 응답의 res_list가 목록 형식이 아닙니다.");
        }
        return list;
    }

    private void requireCount(String count, List<?> items, int maximum) {
        if (items == null || items.isEmpty() || items.size() > maximum || items.stream().anyMatch(Objects::isNull)
                || !String.valueOf(items.size()).equals(count)) {
            throw new IllegalArgumentException("요청건수와 목록이 일치해야 하며 최대 " + maximum + "건까지 가능합니다.");
        }
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "이(가) 필요합니다.");
        }
    }
}
