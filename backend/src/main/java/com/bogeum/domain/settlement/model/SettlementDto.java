package com.bogeum.domain.settlement.model;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 정산 계좌 등록·조회의 요청과 표시용 응답. */
public class SettlementDto {
	/** 등록 시 은행 검증을 수행하고, 송금 시 예금주 검증을 다시 수행하는 본인 명의 계좌. */
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AccountRequest {
		@NotBlank(message = "은행 코드를 입력해 주세요.")
		@Pattern(regexp = "[0-9]{3}", message = "은행 코드는 세 자리입니다.")
		private String bankCode;
		@NotBlank(message = "계좌번호를 입력해 주세요.")
		@Pattern(regexp = "[0-9]{4,16}", message = "계좌번호는 숫자 4~16자리로 입력해 주세요.")
		private String accountNumber;
		@NotBlank(message = "예금주를 입력해 주세요.")
		@Size(max = 50, message = "예금주 이름은 50자까지 입력해 주세요.")
		private String holderName;
		@NotBlank(message = "계좌 실명조회에 필요한 생년월일을 입력해 주세요.")
		@Pattern(regexp = "[0-9]{6}", message = "생년월일은 yyMMdd 여섯 자리로 입력해 주세요.")
		private String birthDate;
	}

	/** 실제 계좌번호와 암호문을 제외한 정산 계좌. */
	@Getter
	@Builder
	public static class AccountResponse {
		private Long settlementAccountId;
		private String bankCode;
		private String accountNumberMasked;
		private String holderName;
		private LocalDateTime verifiedAt;

		/** 계좌번호는 마스킹된 값만 반환한다. */
		public static AccountResponse from(SettlementAccount account) {
			return AccountResponse.builder().settlementAccountId(account.getSettlementAccountId())
				.bankCode(account.getBankCode()).accountNumberMasked(account.getAccountNumberMasked())
				.holderName(account.getHolderName()).verifiedAt(account.getVerifiedAt()).build();
		}
	}
}
