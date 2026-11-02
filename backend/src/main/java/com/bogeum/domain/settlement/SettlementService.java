package com.bogeum.domain.settlement;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.openbanking.OpenBankingException;
import com.bogeum.domain.openbanking.support.OpenBankingClient;
import com.bogeum.domain.openbanking.support.OpenBankingClient.TransferResult;
import com.bogeum.domain.settlement.model.SettlementAccount;
import com.bogeum.domain.settlement.repository.SettlementAccountRepository;
import com.bogeum.domain.settlement.service.SettlementTransferService;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.utils.SecretCipher;

import lombok.extern.slf4j.Slf4j;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService {
	private final SettlementAccountRepository accounts;
	private final UserRepository users;
	private final SecretCipher cipher;
	private final OpenBankingClient client;
	private final SettlementTransferService transfers;

	@Transactional
	public SettlementAccount registerAccount(Long landlordId, String bankCode, String accountNumber, String holderName) {
		var user = users.findLockedById(landlordId).orElseThrow(() -> OpenBankingException.of(ErrorCode.AUTH_REQUIRED));
		if (bankCode == null || !bankCode.matches("[0-9]{3}") || accountNumber == null
				|| !accountNumber.matches("[0-9]{4,16}") || !user.getName().equals(holderName)) {
			throw OpenBankingException.of(ErrorCode.INVALID_SETTLEMENT_ACCOUNT);
		}
		String masked = "*".repeat(accountNumber.length() - 3) + accountNumber.substring(accountNumber.length() - 3);
		var account = accounts.findByLandlordUserId(landlordId)
			.orElseGet(() -> SettlementAccount.builder().landlord(user).build());
		account.changeAccount(bankCode, cipher.encrypt(accountNumber), masked, holderName);
		return accounts.save(account);
	}

	@Transactional(readOnly = true)
	public SettlementAccount findAccount(Long landlordId) {
		return accounts.findByLandlordUserId(landlordId).orElse(null);
	}

	public void settle(Long rentPaymentId) {
		String token = client.issueInstitutionToken().accessToken();
		var deposit = transfers.prepare(rentPaymentId, client.newBankTranId());
		if (deposit == null) {
			return;
		}
		TransferResult result;
		try {
			result = client.deposit(token, deposit.bankTranId(), deposit.bankCode(), deposit.accountNumber(),
				deposit.holderName(), deposit.amount(), deposit.tenantName(), deposit.tenantNum(), deposit.tenantFintechUseNum());
		} catch (RuntimeException exception) {
			transfers.complete(rentPaymentId, false, "RESPONSE_UNKNOWN");
			throw exception;
		}
		transfers.complete(rentPaymentId, result.succeeded(), result.code());
	}

	public void trySettle(Long rentPaymentId) {
		try {
			settle(rentPaymentId);
		} catch (RuntimeException exception) {
			// 예외 원문에는 계좌나 토큰이 포함될 수 있으므로 식별자와 예외 종류만 기록한다.
			log.warn("월세 지급 확인 필요: rentPaymentId={}, error={}", rentPaymentId, exception.getClass().getSimpleName());
		}
	}
}
