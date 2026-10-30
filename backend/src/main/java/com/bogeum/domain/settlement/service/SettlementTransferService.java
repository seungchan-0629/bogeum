package com.bogeum.domain.settlement.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.common.enums.PaymentAttemptStatus;
import com.bogeum.common.enums.RentPaymentStatus;
import com.bogeum.common.enums.SettlementStatus;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.openbanking.OpenBankingException;
import com.bogeum.domain.rent.repository.PaymentAttemptRepository;
import com.bogeum.domain.settlement.SettlementRepository;
import com.bogeum.domain.settlement.repository.SettlementAccountRepository;
import com.bogeum.utils.SecretCipher;

import lombok.RequiredArgsConstructor;

/** 지급 요청을 먼저 커밋하여 응답 유실·서버 재시작 후 중복 송금을 막는다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class SettlementTransferService {
	private final SettlementRepository settlements;
	private final SettlementAccountRepository accounts;
	private final PaymentAttemptRepository attempts;
	private final SecretCipher cipher;

	public Deposit prepare(Long rentPaymentId, String bankTranId) {
		var settlement = settlements.findByRentPaymentRentPaymentId(rentPaymentId)
			.orElseThrow(() -> OpenBankingException.of(ErrorCode.SETTLEMENT_NOT_FOUND));
		if (settlement.getSettlementStatus() == SettlementStatus.COMPLETED) {
			return null;
		}
		if (settlement.getSettlementStatus() != SettlementStatus.PENDING) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS);
		}
		var rent = settlement.getRentPayment();
		if (rent.getRentPaymentStatus() != RentPaymentStatus.PAID
				|| settlement.getAmount().compareTo(rent.getAmount()) != 0) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS);
		}
		var account = accounts.findByLandlordUserId(settlement.getLandlord().getUserId()).orElse(null);
		if (account == null) {
			return null;
		}
		var attempt = attempts.findFirstByRentPaymentRentPaymentIdOrderByAttemptNoDesc(rentPaymentId)
			.filter(value -> value.getAttemptStatus() == PaymentAttemptStatus.SUCCEEDED
				&& value.getBankTranId() != null && value.getBankTranId().length() == 20
				&& value.getPaymentMethod().getFintechUseNum() != null
				&& value.getPaymentMethod().getFintechUseNum().matches("[0-9]{24}"))
			.orElseThrow(() -> OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS));
		String accountNumber = cipher.decrypt(account.getAccountNumberEncrypted());
		settlement.startTransfer(account, bankTranId);
		return new Deposit(bankTranId, account.getBankCode(), accountNumber, account.getHolderName(),
			settlement.getAmount(), rent.getContract().getTenant().getName(),
			rent.getContract().getTenant().getUserId().toString(), attempt.getPaymentMethod().getFintechUseNum());
	}

	public void complete(Long rentPaymentId, boolean succeeded, String code) {
		var settlement = settlements.findByRentPaymentRentPaymentId(rentPaymentId).orElseThrow();
		if (settlement.getSettlementStatus() != SettlementStatus.PROCESSING) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS);
		}
		if (succeeded) {
			settlement.complete();
		} else {
			settlement.markUnknown(code);
		}
	}

	public record Deposit(String bankTranId, String bankCode, String accountNumber, String holderName,
			BigDecimal amount, String tenantName, String tenantNum, String tenantFintechUseNum) {
		@Override
		public String toString() { return "Deposit[계좌정보 생략]"; }
	}
}
