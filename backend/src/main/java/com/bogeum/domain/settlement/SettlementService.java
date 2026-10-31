package com.bogeum.domain.settlement;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bogeum.common.enums.PaymentAttemptStatus;
import com.bogeum.common.enums.RentPaymentStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.common.enums.SettlementStatus;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.openbanking.OpenBankingException;
import com.bogeum.domain.openbanking.OpenBankingClient;
import com.bogeum.domain.openbanking.OpenBankingClient.TransferResult;
import com.bogeum.domain.rent.RentRepository.PaymentAttemptRepository;
import com.bogeum.domain.settlement.SettlementRepository.SettlementAccountRepository;
import com.bogeum.domain.settlement.SettlementRepository.SettlementAttemptRepository;
import com.bogeum.domain.settlement.SettlementRepository.SettlementResolutionRepository;
import com.bogeum.domain.settlement.model.SettlementAccount;
import com.bogeum.domain.settlement.model.SettlementAttempt;
import com.bogeum.domain.settlement.model.SettlementResolution;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.utils.SecretCipher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService {
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
	private final SettlementAccountRepository accounts;
	private final UserRepository users;
	private final SecretCipher cipher;
	private final OpenBankingClient client;
	private final SettlementRepository settlements;
	private final PaymentAttemptRepository attempts;
	private final PlatformTransactionManager transactionManager;
	private final SettlementAttemptRepository settlementAttempts;
	private final SettlementResolutionRepository resolutions;

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public SettlementAccount registerAccount(Long landlordId, String bankCode, String accountNumber,
			String holderName, String birthDate) {
		var user = users.findById(landlordId).orElseThrow(() -> OpenBankingException.of(ErrorCode.AUTH_REQUIRED));
		if (bankCode == null || !bankCode.matches("[0-9]{3}") || accountNumber == null
				|| !accountNumber.matches("[0-9]{4,16}") || !user.getName().equals(holderName)
				|| !validBirthDate(birthDate)) {
			throw OpenBankingException.of(ErrorCode.INVALID_SETTLEMENT_ACCOUNT);
		}
		// 생년월일은 은행 검증에만 전달하며, DB·응답·로그에는 보관하지 않는다.
		client.verifyAccount(bankCode, accountNumber, holderName, birthDate);
		return newTransaction().execute(status -> {
			var lockedUser = users.findLockedById(landlordId)
				.orElseThrow(() -> OpenBankingException.of(ErrorCode.AUTH_REQUIRED));
			if (!lockedUser.getName().equals(holderName)) {
				throw OpenBankingException.of(ErrorCode.INVALID_SETTLEMENT_ACCOUNT);
			}
			String masked = "*".repeat(accountNumber.length() - 3) + accountNumber.substring(accountNumber.length() - 3);
			var account = accounts.findByLandlordUserId(landlordId)
				.orElseGet(() -> SettlementAccount.builder().landlord(lockedUser).build());
			account.changeAccount(bankCode, cipher.encrypt(accountNumber), masked, holderName);
			account.markVerified(LocalDateTime.now(KOREA));
			return accounts.save(account);
		});
	}

	@Transactional(readOnly = true)
	public SettlementAccount findAccount(Long landlordId) {
		return accounts.findByLandlordUserId(landlordId).orElse(null);
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void settle(Long rentPaymentId) {
		var transaction = newTransaction();
		String token = client.issueInstitutionToken().accessToken();
		String bankTranId = client.newBankTranId();
		String institutionReference = client.institutionAccountReference();
		var deposit = transaction.execute(status -> prepare(rentPaymentId, bankTranId, institutionReference));
		if (deposit == null) {
			return;
		}
		TransferResult result;
		try {
			result = client.deposit(token, deposit.bankTranId(), deposit.bankCode(), deposit.accountNumber(),
				deposit.holderName(), deposit.amount(), deposit.tenantName(), deposit.tenantNum(), deposit.tenantFintechUseNum());
		} catch (RuntimeException exception) {
			transaction.executeWithoutResult(status -> complete(rentPaymentId, deposit.attemptId(),
				deposit.bankTranId(), false, "RESPONSE_UNKNOWN"));
			throw exception;
		}
		transaction.executeWithoutResult(status -> complete(rentPaymentId, deposit.attemptId(),
			deposit.bankTranId(), result.succeeded(), result.code()));
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void trySettle(Long rentPaymentId) {
		try {
			settle(rentPaymentId);
		} catch (RuntimeException exception) {
			log.warn("월세 지급 확인 필요: rentPaymentId={}, error={}", rentPaymentId, exception.getClass().getSimpleName());
		}
	}

	/** 은행에서 별도로 확보한 증빙을 근거로 현재 원거래만 확정한다. */
	@Transactional
	public void resolve(Long rentPaymentId, String bankTranId, boolean succeeded,
			String evidenceReference, Long adminId) {
		if (adminId == null || adminId <= 0 || evidenceReference == null || evidenceReference.isBlank()
				|| evidenceReference.length() > 500 || bankTranId == null || bankTranId.length() != 20) {
			throw OpenBankingException.of(ErrorCode.INVALID_REQUEST);
		}
		users.findById(adminId).filter(user -> user.getRole() == Role.ADMIN)
			.orElseThrow(() -> OpenBankingException.of(ErrorCode.ACCESS_DENIED));
		var settlement = settlements.findByRentPaymentRentPaymentId(rentPaymentId)
			.orElseThrow(() -> OpenBankingException.of(ErrorCode.SETTLEMENT_NOT_FOUND));
		var attempt = settlementAttempts.findFirstBySettlementSettlementIdOrderByAttemptNoDesc(settlement.getSettlementId())
			.orElseThrow(() -> OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS));
		if ((settlement.getSettlementStatus() != SettlementStatus.UNKNOWN
				&& settlement.getSettlementStatus() != SettlementStatus.PROCESSING)
				|| !attempt.isUnresolved() || !bankTranId.equals(settlement.getBankTranId())
				|| !bankTranId.equals(attempt.getBankTranId())) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS);
		}
		if (settlement.getSettlementStatus() == SettlementStatus.PROCESSING
				&& (attempt.getRequestedAt() == null
					|| !attempt.getRequestedAt().isBefore(LocalDateTime.now(KOREA).minusMinutes(10)))) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS);
		}
		resolutions.save(SettlementResolution.builder().settlementAttempt(attempt).adminUserId(adminId)
			.evidenceReference(evidenceReference.strip()).previousStatus(attempt.getAttemptStatus())
			.succeeded(succeeded).resolvedAt(LocalDateTime.now(KOREA)).build());
		if (succeeded) {
			attempt.succeed("MANUAL_CONFIRMED");
			settlement.complete();
		} else {
			attempt.failConfirmed();
			settlement.failConfirmed();
		}
	}

	/** 외부 호출 전에 요청·스냅샷을 커밋한다. FAILED 재시도는 명시적 호출만 허용한다. */
	private Deposit prepare(Long rentPaymentId, String bankTranId, String institutionReference) {
		var settlement = settlements.findByRentPaymentRentPaymentId(rentPaymentId)
			.orElseThrow(() -> OpenBankingException.of(ErrorCode.SETTLEMENT_NOT_FOUND));
		if (settlement.getSettlementStatus() == SettlementStatus.COMPLETED) {
			return null;
		}
		if (settlement.getSettlementStatus() != SettlementStatus.PENDING
				&& settlement.getSettlementStatus() != SettlementStatus.FAILED) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS);
		}
		var previous = settlementAttempts.findFirstBySettlementSettlementIdOrderByAttemptNoDesc(settlement.getSettlementId());
		if (previous.isPresent() && previous.get().getAttemptStatus() != SettlementAttempt.Status.FAILED) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS);
		}
		// 과거 시스템의 FAILED 단일행을 실패 증빙이 있는 새 저널로 오인하지 않는다.
		if (settlement.getSettlementStatus() == SettlementStatus.FAILED && previous.isEmpty()) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS);
		}
		var rent = settlement.getRentPayment();
		if (rent.getRentPaymentStatus() != RentPaymentStatus.PAID
				|| settlement.getAmount().compareTo(rent.getAmount()) != 0) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS);
		}
		var account = accounts.findByLandlordUserId(settlement.getLandlord().getUserId()).orElse(null);
		if (account == null || account.getVerifiedAt() == null) {
			return null;
		}
		var collection = attempts.findFirstByRentPaymentRentPaymentIdOrderByAttemptNoDesc(rentPaymentId)
			.filter(value -> value.getAttemptStatus() == PaymentAttemptStatus.SUCCEEDED
				&& value.getBankTranId() != null && value.getBankTranId().length() == 20
				&& value.getAmount().compareTo(settlement.getAmount()) == 0
				&& institutionReference != null && institutionReference.matches("[a-fA-F0-9]{64}")
				&& institutionReference.equals(value.getInstitutionAccountReference())
				&& value.getFintechUseNumSnapshot() != null && value.getFintechUseNumSnapshot().matches("[0-9]{24}")
				&& value.getTenantNameSnapshot() != null && !value.getTenantNameSnapshot().isBlank()
				&& value.getTenantUserIdSnapshot() != null && value.getTenantUserIdSnapshot() > 0)
			.orElseThrow(() -> OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS));
		var attempt = settlementAttempts.save(SettlementAttempt.builder().settlement(settlement)
			.collectionAttempt(collection).attemptNo(previous.map(value -> value.getAttemptNo() + 1).orElse(1))
			.bankTranId(bankTranId).institutionAccountReference(institutionReference).amount(settlement.getAmount())
			.tenantFintechUseNumSnapshot(collection.getFintechUseNumSnapshot())
			.tenantNameSnapshot(collection.getTenantNameSnapshot()).tenantUserIdSnapshot(collection.getTenantUserIdSnapshot())
			.bankCodeSnapshot(account.getBankCode()).accountNumberEncryptedSnapshot(account.getAccountNumberEncrypted())
			.accountNumberMaskedSnapshot(account.getAccountNumberMasked())
			.holderNameSnapshot(account.getHolderName()).build());
		settlement.startTransfer(account, bankTranId);
		return new Deposit(attempt.getSettlementAttemptId(), bankTranId, attempt.getBankCodeSnapshot(),
			cipher.decrypt(attempt.getAccountNumberEncryptedSnapshot()), attempt.getHolderNameSnapshot(), attempt.getAmount(),
			attempt.getTenantNameSnapshot(), attempt.getTenantUserIdSnapshot().toString(), attempt.getTenantFintechUseNumSnapshot());
	}

	private void complete(Long rentPaymentId, Long attemptId, String bankTranId, boolean succeeded, String code) {
		var settlement = settlements.findByRentPaymentRentPaymentId(rentPaymentId).orElseThrow();
		var attempt = settlementAttempts.findById(attemptId)
			.orElseThrow(() -> OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS));
		if (!Objects.equals(attempt.getSettlement().getSettlementId(), settlement.getSettlementId())
				|| !bankTranId.equals(attempt.getBankTranId())) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS);
		}
		// 관리자가 먼저 원거래를 확정했거나 이후 시도가 시작됐다면 늦은 응답으로 덮지 않는다.
		if (!attempt.isUnresolved()) {
			if (succeeded != (attempt.getAttemptStatus() == SettlementAttempt.Status.SUCCEEDED)) {
				log.warn("지급 확정 결과와 늦은 응답 비교 필요: rentPaymentId={}, settlementAttemptId={}", rentPaymentId, attemptId);
			}
			return;
		}
		if (settlement.getSettlementStatus() != SettlementStatus.PROCESSING
				|| !bankTranId.equals(settlement.getBankTranId())) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS);
		}
		if (succeeded) {
			attempt.succeed(code);
			settlement.complete();
		} else {
			attempt.markUnknown(code);
			settlement.markUnknown(code);
		}
	}

	private TransactionTemplate newTransaction() {
		var transaction = new TransactionTemplate(transactionManager);
		transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return transaction;
	}

	private boolean validBirthDate(String value) {
		if (value == null || !value.matches("[0-9]{6}")) {
			return false;
		}
		try {
			LocalDate.of(2000 + Integer.parseInt(value.substring(0, 2)),
				Integer.parseInt(value.substring(2, 4)), Integer.parseInt(value.substring(4, 6)));
			return true;
		} catch (DateTimeException exception) {
			return false;
		}
	}

	private record Deposit(Long attemptId, String bankTranId, String bankCode, String accountNumber, String holderName,
			BigDecimal amount, String tenantName, String tenantNum, String tenantFintechUseNum) {
		@Override
		public String toString() { return "Deposit[계좌정보 생략]"; }
	}
}
