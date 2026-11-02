package com.bogeum.domain.rent.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.bogeum.common.enums.PaymentMethodStatus;
import com.bogeum.common.enums.RentPaymentStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 출금 계좌와 월세 납부의 요청·응답. */
public class RentDto {
	/** OAuth에 연결된 본인 계좌 중 하나를 선택한다. */
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PaymentAccountRequest {
		@NotBlank(message = "출금 계좌를 선택해 주세요.")
		@Pattern(regexp = "[0-9]{24}", message = "핀테크이용번호를 확인해 주세요.")
		private String fintechUseNum;
	}

	/** 저장된 출금 계좌의 표시용 정보. */
	@Getter
	@Builder
	public static class PaymentAccountResponse {
		private Long paymentMethodId;
		private String bankCode;
		private String accountNumberMasked;
		private PaymentMethodStatus paymentMethodStatus;

		/** 계좌 식별자와 인증 정보는 노출하지 않는다. */
		public static PaymentAccountResponse from(PaymentMethod method) {
			return PaymentAccountResponse.builder().paymentMethodId(method.getPaymentMethodId())
				.bankCode(method.getBankCode()).accountNumberMasked(method.getAccountNumberMasked())
				.paymentMethodStatus(method.getPaymentMethodStatus()).build();
		}
	}

	/** 임차인의 월세 납부 결과. */
	@Getter
	@Builder
	public static class PaymentResponse {
		private Long rentPaymentId;
		private Integer seq;
		private BigDecimal amount;
		private RentPaymentStatus rentPaymentStatus;
		private LocalDateTime dueAt;
		private LocalDateTime paidAt;

		/** 지연 로딩 관계와 민감한 계좌 정보는 매핑하지 않는다. */
		public static PaymentResponse from(RentPayment rent) {
			return PaymentResponse.builder().rentPaymentId(rent.getRentPaymentId()).seq(rent.getSeq())
				.amount(rent.getAmount()).rentPaymentStatus(rent.getRentPaymentStatus())
				.dueAt(rent.getDueAt()).paidAt(rent.getPaidAt()).build();
		}
	}
}
