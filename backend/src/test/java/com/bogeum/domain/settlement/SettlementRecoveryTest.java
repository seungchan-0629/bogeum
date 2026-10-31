package com.bogeum.domain.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.bogeum.common.enums.PaymentAttemptStatus;
import com.bogeum.common.enums.RentPaymentStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.common.enums.SettlementStatus;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.openbanking.OpenBankingClient;
import com.bogeum.domain.openbanking.OpenBankingClient.Token;
import com.bogeum.domain.openbanking.OpenBankingClient.TransferResult;
import com.bogeum.domain.openbanking.OpenBankingException;
import com.bogeum.domain.rent.RentRepository.PaymentAttemptRepository;
import com.bogeum.domain.rent.model.PaymentAttempt;
import com.bogeum.domain.rent.model.RentPayment;
import com.bogeum.domain.settlement.SettlementRepository.SettlementAccountRepository;
import com.bogeum.domain.settlement.SettlementRepository.SettlementAttemptRepository;
import com.bogeum.domain.settlement.SettlementRepository.SettlementResolutionRepository;
import com.bogeum.domain.settlement.model.Settlement;
import com.bogeum.domain.settlement.model.SettlementAccount;
import com.bogeum.domain.settlement.model.SettlementAttempt;
import com.bogeum.domain.settlement.model.SettlementResolution;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.SecretCipher;

/** 외부 호출 없이 지급 복구 권한·증빙·요청 스냅샷의 안전 조건을 검증한다. */
@ExtendWith(MockitoExtension.class)
class SettlementRecoveryTest {
	private static final Long RENT_ID = 10L;
	private static final Long SETTLEMENT_ID = 20L;
	private static final Long ADMIN_ID = 30L;
	private static final String TRAN_ID = "T000000001U000000001";
	private static final BigDecimal AMOUNT = new BigDecimal("650000");
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	@Mock private SettlementAccountRepository accounts;
	@Mock private UserRepository users;
	@Mock private SecretCipher cipher;
	@Mock private OpenBankingClient client;
	@Mock private SettlementRepository settlements;
	@Mock private PaymentAttemptRepository collections;
	@Mock private PlatformTransactionManager transactionManager;
	@Mock private SettlementAttemptRepository attempts;
	@Mock private SettlementResolutionRepository resolutions;
	private SettlementService service;

	@BeforeEach
	void createService() {
		service = new SettlementService(accounts, users, cipher, client, settlements, collections,
			transactionManager, attempts, resolutions);
	}

	@Test
	void unknownSuccessRequiresAndStoresEvidenceBeforeCompleting() {
		var fixture = unresolved(SettlementStatus.UNKNOWN, 1);
		service.resolve(RENT_ID, TRAN_ID, true, "bank-receipt-123", ADMIN_ID);
		assertEquals(SettlementStatus.COMPLETED, fixture.settlement().getSettlementStatus());
		assertEquals(SettlementAttempt.Status.SUCCEEDED, fixture.attempt().getAttemptStatus());
		var audit = ArgumentCaptor.forClass(SettlementResolution.class);
		verify(resolutions).save(audit.capture());
		assertEquals("bank-receipt-123", audit.getValue().getEvidenceReference());
		assertEquals(ADMIN_ID, audit.getValue().getAdminUserId());
		assertEquals(SettlementAttempt.Status.UNKNOWN, audit.getValue().getPreviousStatus());
		assertEquals(true, audit.getValue().isSucceeded());
		assertNotNull(audit.getValue().getResolvedAt());
		assertThrows(OpenBankingException.class,
			() -> service.resolve(RENT_ID, TRAN_ID, false, "later-receipt", ADMIN_ID));
		assertEquals(SettlementStatus.COMPLETED, fixture.settlement().getSettlementStatus());
	}

	@Test
	void differentOriginalTransactionCannotResolveCurrentAttempt() {
		var fixture = unresolved(SettlementStatus.UNKNOWN, 1);
		assertThrows(OpenBankingException.class,
			() -> service.resolve(RENT_ID, "T000000001U000000002", false, "bank-receipt-123", ADMIN_ID));
		assertEquals(SettlementStatus.UNKNOWN, fixture.settlement().getSettlementStatus());
		verifyNoInteractions(resolutions);
	}

	@Test
	void activeRequestCannotBeManuallyFailedWhileBankCallMayStillBeRunning() {
		var fixture = unresolved(SettlementStatus.PROCESSING, 1);
		assertThrows(OpenBankingException.class,
			() -> service.resolve(RENT_ID, TRAN_ID, false, "bank-receipt-123", ADMIN_ID));
		assertEquals(SettlementStatus.PROCESSING, fixture.settlement().getSettlementStatus());
		verifyNoInteractions(resolutions);
	}

	@Test
	void staleRequestCanBeConfirmedFailedAndKeepsFailedOriginalWithAudit() {
		var fixture = unresolved(SettlementStatus.PROCESSING, 11);
		service.resolve(RENT_ID, TRAN_ID, false, "bank-failure-123", ADMIN_ID);
		assertEquals(SettlementStatus.FAILED, fixture.settlement().getSettlementStatus());
		assertEquals(SettlementAttempt.Status.FAILED, fixture.attempt().getAttemptStatus());
		assertEquals(TRAN_ID, fixture.attempt().getBankTranId());
		var audit = ArgumentCaptor.forClass(SettlementResolution.class);
		verify(resolutions).save(audit.capture());
		assertEquals(false, audit.getValue().isSucceeded());
		assertEquals(SettlementAttempt.Status.REQUESTED, audit.getValue().getPreviousStatus());
	}

	@Test
	void manualResolutionRequiresAnExistingAdminAndNonemptyEvidence() {
		assertThrows(OpenBankingException.class,
			() -> service.resolve(RENT_ID, TRAN_ID, true, " ", ADMIN_ID));
		verifyNoInteractions(users, settlements, resolutions);
		when(users.findById(ADMIN_ID)).thenReturn(Optional.of(User.builder().role(Role.USER).build()));
		var error = assertThrows(OpenBankingException.class,
			() -> service.resolve(RENT_ID, TRAN_ID, true, "bank-receipt", ADMIN_ID));
		assertEquals(ErrorCode.ACCESS_DENIED, error.getErrorCode());
		verifyNoInteractions(settlements, resolutions);
	}

	@Test
	void accountVerificationFailureDoesNotReplacePreviouslyRegisteredAccount() {
		when(users.findById(7L)).thenReturn(Optional.of(User.builder().name("임대인").build()));
		doThrow(OpenBankingException.of(ErrorCode.INVALID_SETTLEMENT_ACCOUNT)).when(client)
			.verifyAccount("097", "1234567890", "임대인", "900101");
		assertThrows(OpenBankingException.class,
			() -> service.registerAccount(7L, "097", "1234567890", "임대인", "900101"));
		verifyNoInteractions(accounts, transactionManager, cipher);
	}

	@Test
	void invalidCalendarDateIsRejectedBeforeSendingBirthInformationToBank() {
		when(users.findById(7L)).thenReturn(Optional.of(User.builder().name("임대인").build()));
		assertThrows(OpenBankingException.class,
			() -> service.registerAccount(7L, "097", "1234567890", "임대인", "900230"));
		verifyNoInteractions(client, accounts, transactionManager);
	}

	@Test
	void payoutUsesCollectedTenantSnapshotAndFreezesRecipientBeforeBankCall() {
		var landlord = User.builder().userId(7L).name("현재 임대인").build();
		var rent = RentPayment.builder().rentPaymentId(RENT_ID).amount(AMOUNT)
			.rentPaymentStatus(RentPaymentStatus.PAID).build();
		var settlement = Settlement.builder().settlementId(SETTLEMENT_ID).rentPayment(rent).landlord(landlord)
			.amount(AMOUNT).build();
		var account = SettlementAccount.builder().landlord(landlord).bankCode("097")
			.accountNumberEncrypted("encrypted-original").accountNumberMasked("*******890")
			.holderName("원거래 임대인").verifiedAt(LocalDateTime.now(KOREA)).build();
		var collection = PaymentAttempt.builder().paymentAttemptId(6L).amount(AMOUNT).bankTranId("T000000001U000000009")
			.attemptStatus(PaymentAttemptStatus.SUCCEEDED).fintechUseNumSnapshot("123456789012345678901234")
			.tenantNameSnapshot("수납 당시 임차인").tenantUserIdSnapshot(8L).institutionAccountReference("a".repeat(64)).build();
		when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		when(client.issueInstitutionToken()).thenReturn(new Token("test-token", "", "", "oob", 3600));
		when(client.newBankTranId()).thenReturn(TRAN_ID);
		when(client.institutionAccountReference()).thenReturn("a".repeat(64));
		when(settlements.findByRentPaymentRentPaymentId(RENT_ID)).thenReturn(Optional.of(settlement));
		when(attempts.findFirstBySettlementSettlementIdOrderByAttemptNoDesc(SETTLEMENT_ID)).thenReturn(Optional.empty());
		when(accounts.findByLandlordUserId(7L)).thenReturn(Optional.of(account));
		when(collections.findFirstByRentPaymentRentPaymentIdOrderByAttemptNoDesc(RENT_ID)).thenReturn(Optional.of(collection));
		when(cipher.decrypt("encrypted-original")).thenReturn("1234567890");
		var saved = new AtomicReference<SettlementAttempt>();
		when(attempts.save(any())).thenAnswer(invocation -> {
			SettlementAttempt attempt = invocation.getArgument(0);
			ReflectionTestUtils.setField(attempt, "settlementAttemptId", 40L);
			saved.set(attempt);
			return attempt;
		});
		when(attempts.findById(40L)).thenAnswer(invocation -> Optional.of(saved.get()));
		when(client.deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
			anyString(), anyString(), anyString())).thenAnswer(invocation -> {
			account.changeAccount("004", "encrypted-new", "****123", "바뀐 예금주");
			return new TransferResult(true, "000");
		});
		service.settle(RENT_ID);
		verify(client).deposit("test-token", TRAN_ID, "097", "1234567890", "원거래 임대인", AMOUNT,
			"수납 당시 임차인", "8", "123456789012345678901234");
		assertEquals("097", saved.get().getBankCodeSnapshot());
		assertEquals("encrypted-original", saved.get().getAccountNumberEncryptedSnapshot());
		assertEquals("*******890", saved.get().getAccountNumberMaskedSnapshot());
		assertEquals(SettlementAttempt.Status.SUCCEEDED, saved.get().getAttemptStatus());
		assertEquals(SettlementStatus.COMPLETED, settlement.getSettlementStatus());
		verify(client, never()).verifyAccount(anyString(), anyString(), anyString(), anyString());
	}

	private Fixture unresolved(SettlementStatus status, int minutesAgo) {
		when(users.findById(ADMIN_ID)).thenReturn(Optional.of(User.builder().role(Role.ADMIN).build()));
		var settlement = Settlement.builder().settlementId(SETTLEMENT_ID).bankTranId(TRAN_ID).settlementStatus(status).build();
		var attempt = SettlementAttempt.builder().settlementAttemptId(40L).settlement(settlement).attemptNo(1)
			.bankTranId(TRAN_ID).attemptStatus(status == SettlementStatus.UNKNOWN
				? SettlementAttempt.Status.UNKNOWN : SettlementAttempt.Status.REQUESTED)
			.requestedAt(LocalDateTime.now(KOREA).minusMinutes(minutesAgo)).build();
		when(settlements.findByRentPaymentRentPaymentId(RENT_ID)).thenReturn(Optional.of(settlement));
		when(attempts.findFirstBySettlementSettlementIdOrderByAttemptNoDesc(SETTLEMENT_ID)).thenReturn(Optional.of(attempt));
		return new Fixture(settlement, attempt);
	}

	private record Fixture(Settlement settlement, SettlementAttempt attempt) { }
}
