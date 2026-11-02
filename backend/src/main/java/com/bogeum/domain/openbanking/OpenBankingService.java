package com.bogeum.domain.openbanking;

import com.bogeum.domain.openbanking.FakeKFTC.StateStore;
import com.bogeum.domain.openbanking.model.OpenBanking;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/*
계좌등록, 유저 토큰발급, 기업oob토큰 발급
등록계좌조회 ,이체결과조회 , 토큰 리프래시, 입금, 출금
*/
@Service
@Validated
@RequiredArgsConstructor
public class OpenBankingService
{
    private final OpenBankingRepository openBankingRepository;
    private final OpenBankingTransferRepository openBankingTransferRepository;
    private final StateStore stateStore;


    //계좌 등록
    @Transactional
    public OpenBanking registerAccount(String code, String state)
    {
        Long userId = stateStore.consume(state);

        // 2. 가상 금융결제원 서버 연결
        RestClient restClient = RestClient.create("http://localhost:8090");

        // 3. 인증 코드 전송 준비
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", "bogeum");
        form.add("client_secret", "mock-secret");
        form.add("redirect_uri", "http://localhost:8080/openbanking/callback");

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

        // 8. 엔티티 생성
        OpenBanking openBanking = OpenBanking.builder()
                .userId(userId)
                .userSeqNo(userSeqNo)
                .fintechNum(fintechUseNum)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn))
                .build();

        // 9. DB 저장
        return openBankingRepository.saveAndFlush(openBanking);
    }

}
