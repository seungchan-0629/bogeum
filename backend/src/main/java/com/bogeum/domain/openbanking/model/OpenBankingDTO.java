package com.bogeum.domain.openbanking.model;

/*
계좌등록 요청/응답 , 입금/출금 요청/응답 , 등록계좌조회 요청/응답, 이체결과조회 요청/응답
엑세스토큰 요청 , 리프레시토큰 요청, 유저 토큰 응답
기업oob토큰 요청/응답
*/

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.domain.Slice;

import com.bogeum.domain.openbanking.TransferStatus;
import com.bogeum.domain.openbanking.TransferType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class OpenBankingDTO
{
    // 계좌등록
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountRegisterReq
    {
        private String responseType; // response_type: "code"
        private String clientId;     // client_id
        private String redirectUri;  // redirect_uri
        private String scope;        // scope
        private String state;        // state: 32바이트 난수
        private String authType;     // auth_type: 최초인증 "0", 인증생략 "2"
    }

    @Getter
    @Builder
    public static class AccountRegisterRes
    {
        private String code;       // Authorization Code
        private String scope;      // 권한 범위
        private String clientInfo; // 요청 시 설정한 임의 정보
        private String state;      // 요청 시 설정한 난수
    }

	/** 마이페이지에 표시할 은행명과 마스킹된 계좌번호만 반환한다. */
	@Getter
	@Builder
	public static class AccountResponse {
		private String bankName;
		private String accountNumberMasked;

		/** 등록 계좌의 화면 표시용 정보만 응답으로 변환한다. */
		public static AccountResponse from(OpenBanking account) {
			return AccountResponse.builder()
				.bankName(account.getBankName())
				.accountNumberMasked(account.getAccountNumberMasked())
				.build();
		}
	}

    // 출금
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WithdrawReq
    {
		@NotNull(message = "계약 ID를 입력해 주세요.")
		@Positive(message = "계약 ID는 양수여야 합니다.")
		private Long contractId;

        private String bankTranId;      // 거래고유번호
        private String cntrAccountType; // 약정 계좌/계정 구분
        private String cntrAccountNum;  // 약정 계좌/계정 번호
        private String dpsPrintContent; // 입금계좌 통장 표시 내용
        private String fintechUseNum;   // 출금계좌 핀테크이용번호

        private String tranAmt;   // 거래금액
        private String tranDtime; // 요청일시: yyyyMMddHHmmss

        private String reqClientName;   // 요청고객성명
        private String reqClientNum;    // 요청고객 회원번호
        private String transferPurpose; // 이체용도 코드
    }

    @Getter
    @Builder
    public static class WithdrawRes
    {
        private String apiTranId;  // 금융결제원 API 거래고유번호
        private String apiTranDtm; // API 거래일시, 밀리초 포함
        private String rspCode;    // API 응답코드
        private String rspMessage; // API 응답메시지

        private String bankTranId;   // 출금거래 고유번호
        private String bankTranDate; // 참가기관 거래일자: 이체결과조회에 사용

        private String bankRspCode;    // 참가기관 처리 결과 코드
        private String bankRspMessage; // 참가기관 처리 결과 메시지

        private String tranAmt; // 거래금액
    }

    // 송금
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemittanceReq
    {
		@NotNull(message = "계약 ID를 입력해 주세요.")
		@Positive(message = "계약 ID는 양수여야 합니다.")
		private Long contractId;

        private String cntrAccountType; // 약정 계좌/계정 구분
        private String cntrAccountNum;  // 약정 계좌/계정 번호
        private String wdPassPhrase;    // 입금이체용 암호문구
        private String wdPrintContent;  // 출금계좌 통장 표시 내용
        private String nameCheckOption; // 수취인성명 검증 "on"/"off"

        private String tranDtime; // 요청일시
        private String reqCnt;   // 요청건수: "1"

        private List<RemittanceItemReq> reqList;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemittanceItemReq {

        private String tranNo;        // 거래순번
        private String bankTranId;    // 이번 송금의 거래고유번호
        private String fintechUseNum; // 수취계좌 핀테크이용번호
        private String printContent;  // 입금계좌 통장 표시 내용
        private String tranAmt;       // 거래금액

        private String reqClientName;   // 요청고객성명
        private String reqClientNum;    // 요청고객 회원번호
        private String transferPurpose; // 이체용도 코드
    }

    @Getter
    @Builder
    public static class RemittanceRes
    {
        private String apiTranId;  // 금융결제원 API 거래고유번호
        private String apiTranDtm; // API 거래일시, 밀리초 포함
        private String rspCode;    // API 응답코드
        private String rspMessage; // API 응답메시지

        private List<RemittanceItemRes> resList; // 개별 송금 결과 목록
    }

    @Getter
    @Builder
    public static class RemittanceItemRes {
        private String bankTranId;     // 이번 송금의 거래고유번호
        private String bankTranDate;   // 참가기관 거래일자
        private String bankCodeTran;   // 응답코드를 부여한 참가기관 코드
        private String bankRspCode;    // 참가기관 처리 결과 코드
        private String bankRspMessage; // 참가기관 처리 결과 메시지

    }
    // 등록계좌조회
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisteredAccountReq
    {
        private String userSeqNo;       // 금융결제원 사용자일련번호
        private String includeCancelYn; // 해지계좌 포함 "Y", 제외 "N"
        private String sortOrder;       // 내림차순 "D", 오름차순 "A"
    }

    @Getter
    @Builder
    public static class RegisteredAccountRes
    {
        private String apiTranId;  // 금융결제원 API 거래고유번호
        private String apiTranDtm; // API 거래일시, 밀리초 포함
        private String rspCode;    // API 응답코드
        private String rspMessage; // API 응답메시지

        private String userName; // 사용자명

        private List<RegisteredAccountItemRes> resList; // 등록계좌 목록
    }
    @Getter
    @Builder
    public static class RegisteredAccountItemRes {

        private String fintechUseNum; // 핀테크이용번호

		private String bankName; // 은행명
		private String accountNumberMasked; // 화면 표시용 마스킹된 계좌번호

        private String transferAgreeYn; // 출금서비스 동의 여부: "Y"/"N"
        private String accountState;    // 계좌상태: 사용 "01", 해지 "09"
    }

    // 이체결과조회
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferResultReq
    {
        private String checkType; // 출금 "1", 입금(송금) "2"
        private String tranDtime; // 이번 조회의 요청일시
        private String reqCnt;   // 조회건수: 최대 25건

        private List<TransferResultItemReq> reqList;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferResultItemReq {

        private String tranNo;          // 조회목록 내 순번
        private String orgBankTranId;   // 원거래 거래고유번호
        private String orgBankTranDate; // 원거래 은행 거래일자
        private String orgTranAmt;      // 원거래 거래금액
    }
    @Getter
    @Builder
    public static class TransferResultRes
    {
        private String apiTranId;  // 이번 결과조회 API의 거래고유번호
        private String apiTranDtm; // 이번 결과조회 API의 거래일시
        private String rspCode;    // API 응답코드
        private String rspMessage; // API 응답메시지

        private String resCnt; // 조회된 이체건수

        private List<TransferResultItemRes> resList; // 개별 이체 결과 목록
    }

    @Getter
    @Builder
    public static class TransferResultItemRes {
        private String tranNo; // 조회 요청 목록의 거래순번

        private String bankTranId;     // 조회 대상 이체의 거래고유번호
        private String bankTranDate;   // 조회 대상 이체의 참가기관 거래일자
        private String bankCodeTran;   // 응답코드를 부여한 참가기관 코드
        private String bankRspCode;    // 참가기관 처리 결과 코드
        private String bankRspMessage; // 참가기관 처리 결과 메시지

        private String tranAmt; // 원거래 거래금액
    }

	/** 계약별 출금·송금 내역의 화면 표시 정보. */
	@Getter
	@Builder
	public static class TransferResponse {
		private Long openBankingTransferId;
		private Long contractId;
		private Long propertyId;
		private String roadAddress;
		private String detailAddress;
		private String bankTranId;
		private TransferType transferType;
		private BigDecimal amount;
		private TransferStatus transferStatus;
		private LocalDateTime requestedAt;
		private LocalDate bankTranDate;
		private String bankResponseCode;
		private String bankResponseMessage;

		/** 계약과 매물 정보가 함께 조회된 이체 기록을 응답으로 변환한다. */
		public static TransferResponse from(OpenBankingTransfer transfer) {
			var contract = transfer.getContract();
			var property = contract.getProperty();
			return TransferResponse.builder()
				.openBankingTransferId(transfer.getOpenBankingTransferId())
				.contractId(contract.getContractId()).propertyId(property.getPropertyId())
				.roadAddress(property.getRoadAddress()).detailAddress(property.getDetailAddress())
				.bankTranId(transfer.getBankTranId()).transferType(transfer.getTransfer_type())
				.amount(transfer.getRequestAmount()).transferStatus(transfer.getStatus())
				.requestedAt(transfer.getRequestedAt()).bankTranDate(transfer.getBankTranDate())
				.bankResponseCode(transfer.getBankResponseCode())
				.bankResponseMessage(transfer.getBankResponseMessage()).build();
		}
	}

	/** 최신 이체 ID 순으로 조회한 내역과 다음 페이지 커서. */
	@Getter
	@Builder
	public static class TransferListResponse {
		private List<TransferResponse> transfers;
		private boolean hasNext;
		private Long nextCursor;

		/** 조회 결과를 목록 응답으로 변환한다. */
		public static TransferListResponse from(Slice<OpenBankingTransfer> slice) {
			var transfers = slice.getContent().stream().map(TransferResponse::from).toList();
			return TransferListResponse.builder().transfers(transfers).hasNext(slice.hasNext())
				.nextCursor(slice.hasNext() ? transfers.get(transfers.size() - 1).getOpenBankingTransferId() : null)
				.build();
		}
	}

    // 사용자 액세스토큰 발급
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserTokenReq
    {
        private String code;         // 인증 콜백으로 받은 코드
        private String clientId;     // 이용기관 앱 Client ID
        private String clientSecret; // 이용기관 앱 Client Secret
        private String redirectUri;  // 인증 요청 때 사용한 콜백 URL
        private String grantType;    // "authorization_code"
    }

    // 리프래시 토큰
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenRefreshReq
    {
        private String clientId;
        private String clientSecret;
        private String refreshToken;
        private String scope;
        private String grantType; // "refresh_token"
    }

    // 사용자 토큰 응답: 최초 발급·갱신 공용
    @Getter
    @Builder
    public static class UserTokenRes
    {
        private String accessToken;  // Access Token
        private String tokenType;    // "Bearer"
        private Long expiresIn;      // 유효기간, 초 단위
        private String refreshToken; // Refresh Token
        private String scope;        // 발급된 권한 범위
        private String userSeqNo;    // 금융결제원 사용자일련번호
    }

    // 기관용 OOB 토큰 발급
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstitutionTokenReq
    {
        private String clientId;
        private String clientSecret;
        private String scope;     // "oob"
        private String grantType; // "client_credentials"
    }

    @Getter
    @Builder
    public static class InstitutionTokenRes
    {
        private String accessToken;
        private String tokenType;     // "Bearer"
        private Long expiresIn;       // 유효기간, 초 단위
        private String scope;         // "oob"
        private String clientUseCode; // 이용기관코드
    }
}
