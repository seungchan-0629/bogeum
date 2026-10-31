package com.bogeum.domain.rent;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.common.enums.PaymentAttemptStatus;
import com.bogeum.common.enums.PaymentMethodStatus;
import com.bogeum.common.enums.RentPaymentStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.domain.contract.ContractRepository;
import com.bogeum.domain.contract.model.Contract;
import com.bogeum.domain.openbanking.OpenBankingException;
import com.bogeum.domain.openbanking.OpenBankingService;
import com.bogeum.domain.openbanking.OpenBankingClient;
import com.bogeum.domain.openbanking.OpenBankingClient.TransferResult;
import com.bogeum.domain.rent.RentRepository.PaymentAttemptRepository;
import com.bogeum.domain.rent.RentRepository.PaymentMethodRepository;
import com.bogeum.domain.rent.RentRepository.CollectionResolutionRepository;
import com.bogeum.domain.rent.model.CollectionResolution;
import com.bogeum.domain.rent.model.RentDto.TransferStatus;
import com.bogeum.domain.rent.model.PaymentAttempt;
import com.bogeum.domain.rent.model.RentPayment;
import com.bogeum.domain.settlement.SettlementRepository;
import com.bogeum.domain.settlement.SettlementService;
import com.bogeum.domain.settlement.SettlementRepository.SettlementAccountRepository;
import com.bogeum.domain.settlement.SettlementRepository.SettlementAttemptRepository;
import com.bogeum.domain.settlement.model.Settlement;
import com.bogeum.domain.settlement.model.SettlementAttempt;
import com.bogeum.domain.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RentService {
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
	private static final LocalTime COLLECTION_TIME = LocalTime.of(9, 0);
	private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999");
	private final RentRepository rents;
	private final PaymentMethodRepository methods;
	private final PaymentAttemptRepository attempts;
	private final SettlementRepository settlementRepository;
	private final OpenBankingService openBanking;
	private final OpenBankingClient client;
	private final SettlementService settlements;
	private final PlatformTransactionManager transactionManager;
	private final SettlementAccountRepository settlementAccounts;
	private final ContractRepository contracts;
	private final CollectionResolutionRepository resolutions;
	private final UserRepository users;
	private final SettlementAttemptRepository settlementAttempts;

	/**
	 * 계약 기간 안의 매월 납부일 한국 시각 09:00에 청구한다. 없는 날짜는 말일로 보정한다.
	 * 시작·종료 시각을 포함하며 일할 계산은 하지 않는다. 기존 회차의 금액·기한은 변경하지 않는다.
	 */
	@Transactional
	public List<RentPayment> generatePayments(Long contractId) {
		validateContractId(contractId);
		Contract contract = contracts.findLockedById(contractId)
			.orElseThrow(() -> OpenBankingException.of(ErrorCode.CONTRACT_NOT_FOUND));
		var existing = rents.findByContractContractIdOrderBySeqAsc(contractId);
		if (!existing.isEmpty()) return existing;
		validateRentContract(contract);
		if (contract.getLeaseType() == LeaseType.JEONSE) return List.of();

		YearMonth firstMonth = YearMonth.from(contract.getStartAt());
		long monthCount = ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(contract.getEndAt()));
		List<RentPayment> scheduled = new ArrayList<>();
		for (long offset = 0; offset <= monthCount; offset++) {
			YearMonth month = firstMonth.plusMonths(offset);
			int day = Math.min(contract.getPaymentDay(), month.lengthOfMonth());
			LocalDateTime dueAt = month.atDay(day).atTime(COLLECTION_TIME).atZone(KOREA).toLocalDateTime();
			if (dueAt.isBefore(contract.getStartAt()) || dueAt.isAfter(contract.getEndAt())) continue;
			scheduled.add(RentPayment.builder().contract(contract).seq(scheduled.size() + 1)
				.amount(contract.getMonthlyRent()).dueAt(dueAt).build());
		}
		return rents.saveAllAndFlush(scheduled);
	}

	@Transactional(readOnly = true)
	public List<RentPayment> findContractPayments(Long contractId) {
		validateContractId(contractId);
		if (!contracts.existsById(contractId)) throw OpenBankingException.of(ErrorCode.CONTRACT_NOT_FOUND);
		return rents.findByContractContractIdOrderBySeqAsc(contractId);
	}

	/** 자동 은행 조회가 아니다. 원거래의 최종 결과를 확인한 관리자가 증빙과 함께 확정한다. */
	@Transactional
	public void resolveCollection(Long rentPaymentId, String bankTranId, boolean succeeded, String evidenceReference, Long adminId) {
		if (evidenceReference == null || evidenceReference.isBlank() || evidenceReference.length() > 200) {
			throw OpenBankingException.of(ErrorCode.INVALID_REQUEST);
		}
		if (adminId == null || adminId <= 0) throw OpenBankingException.of(ErrorCode.ACCESS_DENIED);
		users.findById(adminId).filter(user -> user.getRole() == Role.ADMIN)
			.orElseThrow(() -> OpenBankingException.of(ErrorCode.ACCESS_DENIED));
		var rent = rents.findLockedById(rentPaymentId)
			.orElseThrow(() -> OpenBankingException.of(ErrorCode.RENT_PAYMENT_NOT_FOUND));
		var attempt = attempts.findFirstByRentPaymentRentPaymentIdOrderByAttemptNoDesc(rentPaymentId)
			.orElseThrow(() -> OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS));
		boolean unknown = rent.getRentPaymentStatus() == RentPaymentStatus.UNKNOWN
			&& attempt.getAttemptStatus() == PaymentAttemptStatus.UNKNOWN;
		boolean stale = rent.getRentPaymentStatus() == RentPaymentStatus.PROCESSING
			&& attempt.getAttemptStatus() == PaymentAttemptStatus.REQUESTED
			&& attempt.getRequestedAt() != null
			&& attempt.getRequestedAt().isBefore(LocalDateTime.now(KOREA).minusMinutes(10));
		if ((!unknown && !stale) || bankTranId == null || !bankTranId.equals(attempt.getBankTranId())
				|| !bankTranId.matches("[A-Za-z0-9]{20}") || attempt.getFintechUseNumSnapshot() == null
				|| !attempt.getFintechUseNumSnapshot().matches("[0-9]{24}") || attempt.getTenantUserIdSnapshot() == null
				|| attempt.getTenantNameSnapshot() == null || attempt.getTenantNameSnapshot().isBlank()
				|| attempt.getAmount().compareTo(rent.getAmount()) != 0) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS);
		}
		resolutions.save(CollectionResolution.builder().paymentAttempt(attempt).adminUserId(adminId)
			.evidenceReference(evidenceReference.trim()).previousStatus(attempt.getAttemptStatus()).succeeded(succeeded).build());
		if (succeeded) {
			attempt.succeed();
			rent.markPaid();
			settlementRepository.save(Settlement.builder().rentPayment(rent).landlord(rent.getContract().getLandlord())
				.amount(rent.getAmount()).build());
		} else {
			attempt.confirmFailure();
			rent.confirmFailure();
		}
	}

	@Transactional(readOnly = true)
	public TransferStatus transferStatus(Long rentPaymentId) {
		var rent = rents.findById(rentPaymentId).orElseThrow(() -> OpenBankingException.of(ErrorCode.RENT_PAYMENT_NOT_FOUND));
		var history = attempts.findByRentPaymentRentPaymentIdOrderByAttemptNoAsc(rentPaymentId);
		var settlement = rent.getSettlement();
		var payouts = settlement == null ? List.<SettlementAttempt>of() : settlementAttempts
			.findBySettlementSettlementIdOrderByAttemptNoAsc(settlement.getSettlementId());
		return TransferStatus.from(rent, history, payouts);
	}

	/** 납부할 금액·수취인은 DB의 계약에서만 가져온다. */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public RentPayment pay(Long rentPaymentId, Long tenantId) {
		// 조회도 독립된 영속성 컨텍스트를 사용해 이전 납부 상태를 재사용하지 않는다.
		var transaction = new TransactionTemplate(transactionManager);
		transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		// 외부 토큰 발급 실패는 이체 요청을 만들기 전에 처리한다.
		var existing = transaction.execute(status -> findPayment(rentPaymentId, tenantId));
		if (existing.getRentPaymentStatus() == RentPaymentStatus.PAID) {
			return existing;
		}
		if (existing.getRentPaymentStatus() == RentPaymentStatus.PROCESSING
				|| existing.getRentPaymentStatus() == RentPaymentStatus.UNKNOWN) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS);
		}
		String accessToken = openBanking.accessToken(tenantId);
		// 은행 호출 전후를 따로 커밋하여 상위 작업의 롤백에도 재출금을 막는다.
		String bankTranId = client.newBankTranId();
		String institutionReference = client.institutionAccountReference();
		var withdrawal = transaction.execute(status -> prepare(rentPaymentId, tenantId, bankTranId, institutionReference));
		if (withdrawal == null) {
			return transaction.execute(status -> findPayment(rentPaymentId, tenantId));
		}
		TransferResult result;
		try {
			result = client.withdraw(accessToken, withdrawal.bankTranId(), withdrawal.fintechUseNum(),
				withdrawal.amount(), withdrawal.tenantName(), withdrawal.tenantNum());
		} catch (RuntimeException exception) {
			transaction.executeWithoutResult(status ->
				complete(rentPaymentId, tenantId, withdrawal.attemptId(), false, "RESPONSE_UNKNOWN"));
			throw exception;
		}
		transaction.executeWithoutResult(status ->
			complete(rentPaymentId, tenantId, withdrawal.attemptId(), result.succeeded(), result.code()));
		if (result.succeeded()) {
			// 지급 장애가 이미 완료된 임차인 수납을 실패로 바꾸지 않는다.
			settlements.trySettle(rentPaymentId);
		}
		return transaction.execute(status -> findPayment(rentPaymentId, tenantId));
	}

	private Withdrawal prepare(Long rentPaymentId, Long tenantId, String bankTranId, String institutionReference) {
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
		if (rent.getDueAt().isAfter(LocalDateTime.now(KOREA)) || amount == null || amount.signum() <= 0
				|| amount.stripTrailingZeros().scale() > 0 || amount.precision() - amount.scale() > 12) {
			throw OpenBankingException.of(ErrorCode.INVALID_PAYMENT_AMOUNT);
		}
		var destination = settlementAccounts.findByLandlordUserId(rent.getContract().getLandlord().getUserId());
		if (destination.isEmpty() || destination.get().getVerifiedAt() == null) {
			throw OpenBankingException.of(ErrorCode.VERIFIED_SETTLEMENT_ACCOUNT_REQUIRED);
		}
		var active = methods.findByTenantUserIdAndPaymentMethodStatus(tenantId, PaymentMethodStatus.ACTIVE);
		if (active.size() != 1 || active.get(0).getWithdrawalConsentedAt() == null
				|| active.get(0).getFintechUseNum() == null || !active.get(0).getFintechUseNum().matches("[0-9]{24}")) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_ACCOUNT_REQUIRED);
		}
		var method = active.get(0);
		var attempt = attempts.saveAndFlush(PaymentAttempt.builder().rentPayment(rent).paymentMethod(method)
			.attemptNo(previous.map(value -> value.getAttemptNo() + 1).orElse(1))
			.amount(amount).bankTranId(bankTranId).fintechUseNumSnapshot(method.getFintechUseNum())
			.tenantNameSnapshot(rent.getContract().getTenant().getName()).tenantUserIdSnapshot(tenantId)
			.institutionAccountReference(institutionReference).build());
		rent.startCollection();
		return new Withdrawal(attempt.getPaymentAttemptId(), bankTranId, method.getFintechUseNum(),
			amount, rent.getContract().getTenant().getName(), tenantId.toString());
	}

	private void complete(Long rentPaymentId, Long tenantId, Long attemptId, boolean succeeded, String code) {
		var rent = rents.findLockedByIdAndTenantId(rentPaymentId, tenantId).orElseThrow();
		var attempt = attempts.findById(attemptId).orElseThrow();
		// 증빙으로 먼저 확정한 시도는 지연 응답이 도착해도 덮어쓰지 않는다.
		if (attempt.getAttemptStatus() == PaymentAttemptStatus.SUCCEEDED
				|| attempt.getAttemptStatus() == PaymentAttemptStatus.FAILED) return;
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
		settlementRepository.save(Settlement.builder().rentPayment(rent).landlord(rent.getContract().getLandlord())
			.amount(rent.getAmount()).build());
	}

	@Transactional(readOnly = true)
	public List<RentPayment> findPayments(Long tenantId) {
		return rents.findByContractTenantUserIdOrderByDueAtDesc(tenantId);
	}

	@Transactional(readOnly = true)
	public RentPayment findPayment(Long rentPaymentId, Long tenantId) {
		return rents.findByRentPaymentIdAndContractTenantUserId(rentPaymentId, tenantId)
			.orElseThrow(() -> OpenBankingException.of(ErrorCode.RENT_PAYMENT_NOT_FOUND));
	}

	private static void validateContractId(Long contractId) {
		if (contractId == null || contractId <= 0) throw OpenBankingException.of(ErrorCode.INVALID_REQUEST);
	}

	private static void validateRentContract(Contract contract) {
		if (contract.getStartAt() == null || contract.getEndAt() == null
				|| contract.getStartAt().isAfter(contract.getEndAt()) || contract.getLeaseType() == null
				|| contract.getTenant() == null || contract.getTenant().getUserId() == null
				|| contract.getLandlord() == null || contract.getLandlord().getUserId() == null) {
			throw OpenBankingException.of(ErrorCode.INVALID_RENT_SCHEDULE);
		}
		BigDecimal amount = contract.getMonthlyRent();
		if (contract.getLeaseType() == LeaseType.JEONSE) {
			if (amount == null || amount.signum() != 0) throw OpenBankingException.of(ErrorCode.INVALID_RENT_SCHEDULE);
			return;
		}
		if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0
				|| amount.stripTrailingZeros().scale() > 0 || contract.getPaymentDay() == null
				|| contract.getPaymentDay() < 1 || contract.getPaymentDay() > 31) {
			throw OpenBankingException.of(ErrorCode.INVALID_RENT_SCHEDULE);
		}
	}

	private record Withdrawal(Long attemptId, String bankTranId, String fintechUseNum,
			BigDecimal amount, String tenantName, String tenantNum) { }
}
