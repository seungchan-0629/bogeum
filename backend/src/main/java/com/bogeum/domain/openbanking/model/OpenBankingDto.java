package com.bogeum.domain.openbanking.model;

import com.bogeum.domain.openbanking.support.OpenBankingClient.Account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 오픈뱅킹 인증과 등록 계좌의 공개 응답만 담는다. */
public class OpenBankingDto {
	/** 프론트에서 받은 인증 코드와 회원에게 귀속된 일회용 state. */
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CallbackRequest {
		@NotBlank(message = "인증 코드를 확인해 주세요.")
		@Size(max = 1024, message = "인증 코드 길이를 확인해 주세요.")
		private String code;
		@NotBlank(message = "인증 확인값을 확인해 주세요.")
		@Size(max = 128, message = "인증 확인값 길이를 확인해 주세요.")
		private String state;
	}

	/** 금융결제원의 인증 페이지 주소. */
	@Getter
	@Builder
	public static class AuthorizationResponse {
		private String authorizationUrl;
	}

	/** 등록 가능한 계좌 정보. 토큰과 실제 계좌번호는 반환하지 않는다. */
	@Getter
	@Builder
	public static class AccountResponse {
		private String fintechUseNum;
		private String bankCode;
		private String accountNumberMasked;
		private String holderName;
		private boolean withdrawalAgreed;

		/** 은행이 반환한 마스킹된 정보만 매핑한다. */
		public static AccountResponse from(Account account) {
			return AccountResponse.builder().fintechUseNum(account.fintechUseNum()).bankCode(account.bankCode())
				.accountNumberMasked(account.accountNumberMasked()).holderName(account.holderName())
				.withdrawalAgreed(account.withdrawalAgreed()).build();
		}
	}
}
