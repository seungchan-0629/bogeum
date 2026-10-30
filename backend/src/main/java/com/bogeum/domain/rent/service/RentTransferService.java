package com.bogeum.domain.rent.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.common.enums.PaymentAttemptStatus;
import com.bogeum.common.enums.PaymentMethodStatus;
import com.bogeum.common.enums.RentPaymentStatus;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.openbanking.OpenBankingException;
import com.bogeum.domain.rent.RentRepository;
import com.bogeum.domain.rent.model.PaymentAttempt;
import com.bogeum.domain.rent.model.RentPayment;
import com.bogeum.domain.rent.repository.PaymentAttemptRepository;
import com.bogeum.domain.rent.repository.PaymentMethodRepository;
import com.bogeum.domain.settlement.SettlementRepository;
import com.bogeum.domain.settlement.model.Settlement;

import lombok.RequiredArgsConstructor;

/** 은행 호출 전후를 각각 커밋한다. 은행에서 출금한 뒤 DB가 롤백되어도 재출금하지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class RentTransferService {
	private final RentRepository rents;
	private final PaymentMethodRepository methods;
	private final PaymentAttemptRepository attempts;
	private final SettlementRepository settlements;

	public Withdrawal prepare(Long rentPaymentId, Long tenantId, String bankTranId) {
		RentPayment rent = rents.findLockedByIdAndTenantId(rentPaymentId, tenantId)
			.orElseThrow(() -> OpenBankingException.of(ErrorCode.RENT_PAYMENT_NOT_FOUND));
		if (rent.getRentPaymentStatus() == RentPaymentStatus.PAID) {
			return null;
		}
		var previous = attempts.findFirstByRentPaymentRentPaymentIdOrderByAttemptNoDesc(rentPaymentId);
		if (rent.getRentPaymentStatus() == RentPaymentStatus.PROCESSING
				|| rent.getRentPaymentStatus() == RentPaymentStatus.UNKNOWN
				|| previous.filter(attempt -> attempt.getAttemptStatus() != PaymentAttemptStatus.FAILED).isPresent()) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS);
		}
		BigDecimal amount = rent.getAmount();
		if (rent.getDueAt().isAfter(LocalDateTime.now()) || amount == null || amount.signum() <= 0
				|| amount.stripTrailingZeros().scale() > 0 || amount.precision() - amount.scale() > 12) {
			throw OpenBankingException.of(ErrorCode.INVALID_PAYMENT_AMOUNT);
		}
		var active = methods.findByTenantUserIdAndPaymentMethodStatus(tenantId, PaymentMethodStatus.ACTIVE);
		if (active.size() != 1 || active.get(0).getWithdrawalConsentedAt() == null
				|| active.get(0).getFintechUseNum() == null || !active.get(0).getFintechUseNum().matches("[0-9]{24}")) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_ACCOUNT_REQUIRED);
		}
		var method = active.get(0);
		var attempt = attempts.saveAndFlush(PaymentAttempt.builder().rentPayment(rent).paymentMethod(method)
			.attemptNo(previous.map(value -> value.getAttemptNo() + 1).orElse(1))
			.amount(amount).bankTranId(bankTranId).build());
		rent.startCollection();
		return new Withdrawal(attempt.getPaymentAttemptId(), bankTranId, method.getFintechUseNum(),
			amount, rent.getContract().getTenant().getName(), tenantId.toString());
	}

	public void complete(Long rentPaymentId, Long tenantId, Long attemptId, boolean succeeded, String code) {
		var rent = rents.findLockedByIdAndTenantId(rentPaymentId, tenantId).orElseThrow();
		var attempt = attempts.findById(attemptId).orElseThrow();
		if (rent.getRentPaymentStatus() != RentPaymentStatus.PROCESSING
				|| attempt.getAttemptStatus() != PaymentAttemptStatus.REQUESTED
				|| !attempt.getRentPayment().getRentPaymentId().equals(rentPaymentId)) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS);
		}
		if (!succeeded) {
			// 오류코드별 최종 실패 여부가 검증될 때까지 은행 비성공 응답도 재이체하지 않는다.
			attempt.markUnknown(code);
			rent.markUnknown();
			return;
		}
		attempt.succeed();
		rent.markPaid();
		settlements.save(Settlement.builder().rentPayment(rent).landlord(rent.getContract().getLandlord())
			.amount(rent.getAmount()).build());
	}

	public record Withdrawal(Long attemptId, String bankTranId, String fintechUseNum,
			BigDecimal amount, String tenantName, String tenantNum) { }
}
