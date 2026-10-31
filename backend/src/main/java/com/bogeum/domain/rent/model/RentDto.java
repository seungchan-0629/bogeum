package com.bogeum.domain.rent.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.bogeum.common.enums.PaymentAttemptStatus;
import com.bogeum.common.enums.PaymentMethodStatus;
import com.bogeum.common.enums.RentPaymentStatus;
import com.bogeum.common.enums.SettlementStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.bogeum.domain.settlement.model.SettlementAttempt;
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
		private SettlementStatus settlementStatus;
		private LocalDateTime settlementPaidAt;

		/** 지연 로딩 관계와 민감한 계좌 정보는 매핑하지 않는다. */
		public static PaymentResponse from(RentPayment rent) {
			var settlement = rent.getSettlement();
			return PaymentResponse.builder().rentPaymentId(rent.getRentPaymentId()).seq(rent.getSeq())
				.amount(rent.getAmount()).rentPaymentStatus(rent.getRentPaymentStatus())
				.dueAt(rent.getDueAt()).paidAt(rent.getPaidAt())
				.settlementStatus(settlement == null ? null : settlement.getSettlementStatus())
				.settlementPaidAt(settlement == null ? null : settlement.getPaidAt()).build();
		}
	}

	/** 은행 원거래 확인 후 관리자가 제출하는 증빙 참조와 최종 결과. */
	public record ResolutionRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9]{20}") String bankTranId,
		@NotNull Boolean succeeded, @NotBlank @Size(max = 200) String evidenceReference) {}

	public record CollectionAttempt(Long attemptId, Integer attemptNo, String bankTranId, PaymentAttemptStatus status,
		LocalDateTime requestedAt, LocalDateTime completedAt, String failureCode) {
		public static CollectionAttempt from(PaymentAttempt attempt) {
			return new CollectionAttempt(attempt.getPaymentAttemptId(), attempt.getAttemptNo(), attempt.getBankTranId(),
				attempt.getAttemptStatus(), attempt.getRequestedAt(), attempt.getCompletedAt(), attempt.getFailureCode());
		}
	}

	public record PayoutAttempt(Long attemptId, Integer attemptNo, String bankTranId, String status,
		BigDecimal amount, String bankCode, String accountNumberMasked, LocalDateTime requestedAt, LocalDateTime completedAt) {
		public static PayoutAttempt from(SettlementAttempt attempt) {
			return new PayoutAttempt(attempt.getSettlementAttemptId(), attempt.getAttemptNo(), attempt.getBankTranId(),
				attempt.getAttemptStatus().name(), attempt.getAmount(), attempt.getBankCodeSnapshot(),
				attempt.getAccountNumberMaskedSnapshot(), attempt.getRequestedAt(), attempt.getCompletedAt());
		}
	}

	/** 관리자가 조회하는 수납·지급 이력. 계좌 원문·암호문·토큰은 포함하지 않는다. */
	public record TransferStatus(Long rentPaymentId, RentPaymentStatus collectionStatus, SettlementStatus settlementStatus,
		BigDecimal amount, BigDecimal outstandingAmount, String collectionBankTranId, String settlementBankTranId,
		List<CollectionAttempt> collectionAttempts, List<PayoutAttempt> settlementAttempts) {
		public static TransferStatus from(RentPayment rent, List<PaymentAttempt> collections, List<SettlementAttempt> payouts) {
			var settlement = rent.getSettlement();
			var status = settlement == null ? null : settlement.getSettlementStatus();
			boolean outstanding = rent.getRentPaymentStatus() == RentPaymentStatus.PAID && status != SettlementStatus.COMPLETED;
			String collectionId = collections.isEmpty() ? null : collections.get(collections.size() - 1).getBankTranId();
			return new TransferStatus(rent.getRentPaymentId(), rent.getRentPaymentStatus(), status, rent.getAmount(),
				outstanding ? rent.getAmount() : BigDecimal.ZERO, collectionId, settlement == null ? null : settlement.getBankTranId(),
				collections.stream().map(CollectionAttempt::from).toList(), payouts.stream().map(PayoutAttempt::from).toList());
		}
	}
}
