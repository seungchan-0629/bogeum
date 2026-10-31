package com.bogeum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PaymentMethodType;
import com.bogeum.common.enums.RentPaymentStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.contract.model.Contract;
import com.bogeum.domain.openbanking.OpenBankingException;
import com.bogeum.domain.openbanking.OpenBankingService;
import com.bogeum.domain.openbanking.OpenBankingClient;
import com.bogeum.domain.openbanking.OpenBankingClient.Token;
import com.bogeum.domain.openbanking.OpenBankingClient.TransferResult;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.rent.RentService;
import com.bogeum.domain.rent.model.PaymentMethod;
import com.bogeum.domain.rent.model.RentPayment;
import com.bogeum.domain.settlement.SettlementService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.SecretCipher;

import jakarta.persistence.EntityManager;

/** 은행 호출은 모두 대체하고, 실제 H2 트랜잭션으로 수납·지급의 중복 방지를 검증한다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-transfer-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.jpa.show-sql=false",
	"spring.jpa.open-in-view=false",
	"spring.sql.init.mode=never",
	"openbanking.enabled=false",
	"openbanking.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@ActiveProfiles("test")
class OpenBankingTransferTests {
	private static final BigDecimal AMOUNT = new BigDecimal("650000");
	private static final String FINTECH_USE_NUM = "123456789012345678901234";
	private static final String ACCOUNT_NUMBER = "00000000000001";

	private final RentService rents;
	private final SettlementService settlements;
	private final EntityManager entityManager;
	private final JdbcTemplate jdbc;
	private final PlatformTransactionManager transactionManager;
	private final SecretCipher cipher;

	@MockitoBean private OpenBankingClient client;
	@MockitoBean private OpenBankingService openBanking;

	private Fixture fixture;

	@Autowired
	OpenBankingTransferTests(RentService rents, SettlementService settlements,
			EntityManager entityManager, JdbcTemplate jdbc, PlatformTransactionManager transactionManager, SecretCipher cipher) {
		this.rents = rents;
		this.settlements = settlements;
		this.entityManager = entityManager;
		this.jdbc = jdbc;
		this.transactionManager = transactionManager;
		this.cipher = cipher;
	}

	@BeforeEach
	void prepareContractAndMockBank() {
		for (String table : List.of("collection_resolution", "settlement_resolution", "settlement_attempt",
				"settlement", "settlement_account", "payment_attempt", "rent_payment", "payment_method",
				"contract", "property", "user_consent", "user")) {
			jdbc.update("delete from " + table);
		}
		var sequence = new AtomicInteger();
		when(client.newBankTranId()).thenAnswer(invocation -> "T000000001U%09d".formatted(sequence.incrementAndGet()));
		when(client.institutionAccountReference()).thenReturn("a".repeat(64));
		when(openBanking.accessToken(anyLong())).thenReturn("tenant-test-token");
		when(client.issueInstitutionToken()).thenReturn(new Token("institution-test-token", "", "", "oob", 3600));
		when(client.withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
			.thenReturn(new TransferResult(true, "000"));
		when(client.deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString()))
			.thenReturn(new TransferResult(true, "000"));
		fixture = new TransactionTemplate(transactionManager).execute(status -> {
			User tenant = user("테스트 임차인", Role.USER);
			User landlord = user("테스트 임대인", Role.USER);
			User agent = user("테스트 중개사", Role.AGENT);
			User admin = user("테스트 관리자", Role.ADMIN);
			Property property = Property.builder().landlord(landlord).agent(agent).leaseType(LeaseType.MONTHLY)
				.deposit(new BigDecimal("10000000")).monthlyRent(AMOUNT).roadAddress("테스트 주소").build();
			entityManager.persist(property);
			Contract contract = Contract.builder().property(property).landlord(landlord).tenant(tenant).agent(agent)
				.leaseType(LeaseType.MONTHLY).deposit(property.getDeposit()).monthlyRent(AMOUNT).paymentDay(1)
				.startAt(nowKorea().minusMonths(1)).endAt(nowKorea().plusYears(1)).build();
			entityManager.persist(contract);
			entityManager.persist(PaymentMethod.builder().tenant(tenant).methodType(PaymentMethodType.ACCOUNT)
				.fintechUseNum(FINTECH_USE_NUM).bankCode("097").accountNumberMasked("000-***")
				.withdrawalConsentedAt(nowKorea().minusDays(1)).build());
			RentPayment rent = RentPayment.builder().contract(contract).seq(1).amount(AMOUNT)
				.dueAt(nowKorea().minusHours(1)).build();
			entityManager.persist(rent);
			return new Fixture(rent.getRentPaymentId(), tenant.getUserId(), landlord.getUserId(), admin.getUserId());
		});
	}

	@Test
	@DisplayName("임차인 수납을 커밋한 뒤 같은 금액을 임대인에게 한 번만 지급한다")
	void collectsThenSettlesOnce() {
		registerSettlementAccount();
		assertThat(rents.pay(fixture.rentId(), fixture.tenantId()).getRentPaymentStatus()).isEqualTo(RentPaymentStatus.PAID);
		assertThat(rentStatus()).isEqualTo("PAID");
		assertThat(attemptStatus()).isEqualTo("SUCCEEDED");
		assertThat(settlementStatus()).isEqualTo("COMPLETED");
		var response = com.bogeum.domain.rent.model.RentDto.PaymentResponse.from(rents.findPayment(fixture.rentId(), fixture.tenantId()));
		assertThat(response.getSettlementStatus()).isEqualTo(com.bogeum.common.enums.SettlementStatus.COMPLETED);
		assertThat(response.getSettlementPaidAt()).isNotNull();
		assertThat(jdbc.queryForObject("select amount from settlement where rent_payment_id = ?", BigDecimal.class, fixture.rentId()))
			.isEqualByComparingTo(AMOUNT);
		var order = inOrder(client);
		order.verify(client).withdraw("tenant-test-token", "T000000001U000000001", FINTECH_USE_NUM,
			AMOUNT, "테스트 임차인", fixture.tenantId().toString());
		order.verify(client).deposit("institution-test-token", "T000000001U000000002", "097", ACCOUNT_NUMBER,
			"테스트 임대인", AMOUNT, "테스트 임차인", fixture.tenantId().toString(), FINTECH_USE_NUM);
		rents.pay(fixture.rentId(), fixture.tenantId());
		settlements.settle(fixture.rentId());
		verify(client, times(1)).withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString());
		verify(client, times(1)).deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString());
		assertThat(attemptCount()).isEqualTo(1L);
	}

	@Test
	@DisplayName("출금 비성공 응답은 결과 확인 전 지급과 재출금을 모두 막는다")
	void unsuccessfulWithdrawalIsHeldWithoutPayoutOrRetry() {
		registerSettlementAccount();
		when(client.withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
			.thenReturn(new TransferResult(false, "999"));
		rents.pay(fixture.rentId(), fixture.tenantId());
		assertWithdrawalHeld();
		assertThatThrownBy(() -> rents.pay(fixture.rentId(), fixture.tenantId()))
			.isInstanceOfSatisfying(OpenBankingException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS));
		verify(client, times(1)).withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString());
		verify(client, never()).deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("출금 응답 유실은 UNKNOWN으로 커밋하고 후속 요청의 재출금을 차단한다")
	void withdrawalTimeoutKeepsDurableUnknownJournal() {
		registerSettlementAccount();
		when(client.withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
			.thenThrow(OpenBankingException.unavailable());
		assertThatThrownBy(() -> rents.pay(fixture.rentId(), fixture.tenantId())).isInstanceOf(OpenBankingException.class);
		assertWithdrawalHeld();
		assertThatThrownBy(() -> rents.pay(fixture.rentId(), fixture.tenantId())).isInstanceOf(OpenBankingException.class);
		verify(client, times(1)).withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString());
		verify(client, never()).deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("지급 응답 유실에도 임차인 납부는 PAID로 유지하고 재지급을 차단한다")
	void payoutTimeoutDoesNotUndoCollectionOrSendAgain() {
		registerSettlementAccount();
		when(client.deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString()))
			.thenThrow(OpenBankingException.unavailable());
		assertThat(rents.pay(fixture.rentId(), fixture.tenantId()).getRentPaymentStatus()).isEqualTo(RentPaymentStatus.PAID);
		assertThat(rentStatus()).isEqualTo("PAID");
		assertThat(attemptStatus()).isEqualTo("SUCCEEDED");
		assertThat(settlementStatus()).isEqualTo("UNKNOWN");
		assertThatThrownBy(() -> settlements.settle(fixture.rentId())).isInstanceOf(OpenBankingException.class);
		rents.pay(fixture.rentId(), fixture.tenantId());
		verify(client, times(1)).withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString());
		verify(client, times(1)).deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("검증된 정산 계좌가 없으면 은행 출금 전에 수납을 차단한다")
	void missingVerifiedSettlementAccountBlocksWithdrawal() {
		assertThatThrownBy(() -> rents.pay(fixture.rentId(), fixture.tenantId()))
			.isInstanceOfSatisfying(OpenBankingException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VERIFIED_SETTLEMENT_ACCOUNT_REQUIRED));
		assertThat(rentStatus()).isEqualTo("SCHEDULED");
		assertThat(attemptCount()).isZero();
		assertThat(jdbc.queryForObject("select count(*) from settlement where rent_payment_id = ?", Long.class, fixture.rentId()))
			.isZero();
		verify(client, never()).withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString());
		verify(client, never()).deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("과거 PG 성공 기록을 새 오픈뱅킹 수납으로 간주하여 지급하지 않는다")
	void legacyPgCollectionCannotFundOpenBankingPayout() {
		registerSettlementAccount();
		// 수납 성공 뒤 아직 지급 요청을 보내지 못한 상황을 만든다.
		when(client.issueInstitutionToken()).thenThrow(OpenBankingException.unavailable())
			.thenReturn(new Token("institution-test-token", "", "", "oob", 3600));
		rents.pay(fixture.rentId(), fixture.tenantId());
		// 기존 PG 성공 이력은 새 오픈뱅킹 은행거래고유번호를 갖지 않는다.
		jdbc.update("update payment_attempt set bank_tran_id = null where rent_payment_id = ?", fixture.rentId());
		assertThatThrownBy(() -> settlements.settle(fixture.rentId()))
			.isInstanceOfSatisfying(OpenBankingException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS));
		assertThat(settlementStatus()).isEqualTo("PENDING");
		verify(client, never()).deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("다른 사람의 납부건은 은행 토큰 발급과 출금 전에 거절한다")
	void rejectsAnotherUsersPaymentBeforeBankAccess() {
		assertThatThrownBy(() -> rents.pay(fixture.rentId(), fixture.landlordId()))
			.isInstanceOfSatisfying(OpenBankingException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RENT_PAYMENT_NOT_FOUND));
		assertThat(rentStatus()).isEqualTo("SCHEDULED");
		assertThat(attemptCount()).isZero();
		verifyNoInteractions(client, openBanking);
	}

	@Test
	@DisplayName("출금 응답을 기다리는 동안 들어온 동시 납부 요청은 재출금하지 않는다")
	void concurrentPaymentsCreateOneAttemptAndWithdrawOnce() throws Exception {
		registerSettlementAccount();
		var executor = Executors.newFixedThreadPool(2);
		var withdrawalEntered = new CountDownLatch(1);
		var releaseWithdrawal = new CountDownLatch(1);
		when(client.withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
			.thenAnswer(invocation -> {
				assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
				withdrawalEntered.countDown();
				assertThat(releaseWithdrawal.await(10, TimeUnit.SECONDS)).isTrue();
				return new TransferResult(true, "000");
			});
		try {
			var first = executor.submit(this::payConcurrently);
			assertThat(withdrawalEntered.await(10, TimeUnit.SECONDS)).isTrue();
			var second = executor.submit(this::payConcurrently);
			assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);
			assertThat(attemptCount()).isEqualTo(1L);
			assertThat(rentStatus()).isEqualTo("PROCESSING");
			assertThat(attemptStatus()).isEqualTo("REQUESTED");
			releaseWithdrawal.countDown();
			assertThat(first.get(10, TimeUnit.SECONDS)).isInstanceOfSatisfying(RentPayment.class,
				payment -> assertThat(payment.getRentPaymentStatus()).isEqualTo(RentPaymentStatus.PAID));
			verify(client, times(1)).withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString());
		} finally {
			releaseWithdrawal.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("두 요청이 납부 전 상태를 함께 조회해도 잠금에서 경쟁하여 한 번만 출금한다")
	void concurrentScheduledPaymentsCompeteUnderLockAndWithdrawOnce() throws Exception {
		registerSettlementAccount();
		var executor = Executors.newFixedThreadPool(2);
		var completions = new ExecutorCompletionService<Object>(executor);
		var tokensRequested = new CountDownLatch(2);
		var withdrawalEntered = new CountDownLatch(1);
		var releaseWithdrawal = new CountDownLatch(1);
		when(openBanking.accessToken(fixture.tenantId())).thenAnswer(invocation -> {
			tokensRequested.countDown();
			assertThat(tokensRequested.await(10, TimeUnit.SECONDS)).isTrue();
			return "tenant-test-token";
		});
		when(client.withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
			.thenAnswer(invocation -> {
				withdrawalEntered.countDown();
				assertThat(releaseWithdrawal.await(10, TimeUnit.SECONDS)).isTrue();
				return new TransferResult(true, "000");
			});
		try {
			completions.submit(this::payConcurrently);
			completions.submit(this::payConcurrently);
			assertThat(withdrawalEntered.await(10, TimeUnit.SECONDS)).isTrue();
			var rejected = completions.poll(10, TimeUnit.SECONDS);
			assertThat(rejected).isNotNull();
			assertThat(rejected.get()).isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);
			assertThat(rentStatus()).isEqualTo("PROCESSING");
			assertThat(attemptStatus()).isEqualTo("REQUESTED");
			assertThat(attemptCount()).isEqualTo(1L);
			releaseWithdrawal.countDown();
			var completed = completions.poll(10, TimeUnit.SECONDS);
			assertThat(completed).isNotNull();
			assertThat(completed.get()).isInstanceOfSatisfying(RentPayment.class,
				payment -> assertThat(payment.getRentPaymentStatus()).isEqualTo(RentPaymentStatus.PAID));
			verify(openBanking, times(2)).accessToken(fixture.tenantId());
			verify(client, times(1)).withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString());
		} finally {
			releaseWithdrawal.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("상위 작업이 롤백돼도 수납·지급 성공은 별도로 커밋하고 은행 호출 중 트랜잭션은 중단한다")
	void completedTransfersSurviveCallerRollback() {
		registerSettlementAccount();
		when(client.withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
			.thenAnswer(invocation -> {
				assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
				assertThat(rentStatus()).isEqualTo("PROCESSING");
				assertThat(attemptStatus()).isEqualTo("REQUESTED");
				return new TransferResult(true, "000");
			});
		when(client.deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString()))
			.thenAnswer(invocation -> {
				assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
				assertThat(rentStatus()).isEqualTo("PAID");
				assertThat(settlementStatus()).isEqualTo("PROCESSING");
				return new TransferResult(true, "000");
			});
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			rents.pay(fixture.rentId(), fixture.tenantId());
			status.setRollbackOnly();
		});
		assertThat(rentStatus()).isEqualTo("PAID");
		assertThat(attemptStatus()).isEqualTo("SUCCEEDED");
		assertThat(settlementStatus()).isEqualTo("COMPLETED");
		rents.pay(fixture.rentId(), fixture.tenantId());
		assertThat(attemptCount()).isEqualTo(1L);
		verify(client, times(1)).withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString());
		verify(client, times(1)).deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("상위 작업 롤백 뒤에도 응답 유실 저널은 UNKNOWN으로 남아 재출금을 막는다")
	void unknownCollectionSurvivesCallerRollback() {
		registerSettlementAccount();
		when(client.withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
			.thenThrow(OpenBankingException.unavailable());
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
			rents.pay(fixture.rentId(), fixture.tenantId()))).isInstanceOf(OpenBankingException.class);
		assertWithdrawalHeld();
		assertThatThrownBy(() -> rents.pay(fixture.rentId(), fixture.tenantId())).isInstanceOf(OpenBankingException.class);
		verify(client, times(1)).withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString());
	}

	@Test
	@DisplayName("기존에 저장됐어도 은행 검증 시각이 없는 정산 계좌는 출금에 사용할 수 없다")
	void unverifiedLegacySettlementAccountBlocksWithdrawal() {
		registerSettlementAccount();
		jdbc.update("update settlement_account set verified_at = null where landlord_id = ?", fixture.landlordId());

		assertError(ErrorCode.VERIFIED_SETTLEMENT_ACCOUNT_REQUIRED,
			() -> rents.pay(fixture.rentId(), fixture.tenantId()));

		assertThat(rentStatus()).isEqualTo("SCHEDULED");
		assertThat(attemptCount()).isZero();
		verify(client, never()).withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString());
	}

	@Test
	@DisplayName("은행 실명 검증이 실패한 정산 계좌는 저장하지 않는다")
	void rejectedBankAccountIsNotStored() {
		doThrow(OpenBankingException.of(ErrorCode.INVALID_SETTLEMENT_ACCOUNT)).when(client)
			.verifyAccount("097", ACCOUNT_NUMBER, "테스트 임대인", "900101");

		assertError(ErrorCode.INVALID_SETTLEMENT_ACCOUNT,
			() -> settlements.registerAccount(fixture.landlordId(), "097", ACCOUNT_NUMBER, "테스트 임대인", "900101"));

		assertThat(jdbc.queryForObject("select count(*) from settlement_account where landlord_id = ?", Long.class,
			fixture.landlordId())).isZero();
		verify(client, never()).withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString());
	}

	@Test
	@DisplayName("출금 결과 미확정은 관리자 증빙으로 성공 확정한 뒤 지급만 별도로 처리한다")
	void confirmedCollectionSuccessCreatesPendingPayoutWithoutAnotherWithdrawal() {
		registerSettlementAccount();
		when(client.withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
			.thenReturn(new TransferResult(false, "999"));
		rents.pay(fixture.rentId(), fixture.tenantId());
		String original = collectionBankTranId();
		assertError(ErrorCode.ACCESS_DENIED,
			() -> rents.resolveCollection(fixture.rentId(), original, true, "BANK-COLLECT-001", fixture.tenantId()));
		assertError(ErrorCode.PAYMENT_IN_PROGRESS,
			() -> rents.resolveCollection(fixture.rentId(), "T000000001U999999999", true, "BANK-COLLECT-001", fixture.adminId()));

		rents.resolveCollection(fixture.rentId(), original, true, "BANK-COLLECT-001", fixture.adminId());

		assertThat(rentStatus()).isEqualTo("PAID");
		assertThat(attemptStatus()).isEqualTo("SUCCEEDED");
		assertThat(settlementStatus()).isEqualTo("PENDING");
		assertThat(jdbc.queryForObject("select evidence_reference from collection_resolution", String.class))
			.isEqualTo("BANK-COLLECT-001");
		assertThat(jdbc.queryForObject("select admin_user_id from collection_resolution", Long.class)).isEqualTo(fixture.adminId());
		verify(client, never()).deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString());
		settlements.settle(fixture.rentId());
		assertThat(settlementStatus()).isEqualTo("COMPLETED");
		assertError(ErrorCode.PAYMENT_IN_PROGRESS,
			() -> rents.resolveCollection(fixture.rentId(), original, true, "BANK-COLLECT-002", fixture.adminId()));
		assertThat(jdbc.queryForObject("select count(*) from collection_resolution", Long.class)).isEqualTo(1L);
		verify(client, times(1)).withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString());
		verify(client, times(1)).deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("증빙으로 실패 확정된 출금만 새 거래번호로 재시도하고 원거래와 증빙은 보존한다")
	void confirmedCollectionFailureAllowsNewAttemptAndPreservesOriginal() {
		registerSettlementAccount();
		when(client.withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
			.thenReturn(new TransferResult(false, "999"), new TransferResult(true, "000"));
		rents.pay(fixture.rentId(), fixture.tenantId());
		String original = collectionBankTranId();

		rents.resolveCollection(fixture.rentId(), original, false, "BANK-FAILED-001", fixture.adminId());

		assertThat(rentStatus()).isEqualTo("FAILED");
		assertThat(attemptStatus()).isEqualTo("FAILED");
		assertError(ErrorCode.PAYMENT_IN_PROGRESS,
			() -> rents.resolveCollection(fixture.rentId(), original, true, "DUPLICATE", fixture.adminId()));
		assertThat(rents.pay(fixture.rentId(), fixture.tenantId()).getRentPaymentStatus()).isEqualTo(RentPaymentStatus.PAID);
		assertThat(attemptCount()).isEqualTo(2L);
		assertThat(collectionBankTranId()).isNotEqualTo(original);
		assertThat(jdbc.queryForList("select attempt_status from payment_attempt where rent_payment_id = ? order by attempt_no",
			String.class, fixture.rentId())).containsExactly("FAILED", "SUCCEEDED");
		assertThat(jdbc.queryForObject("select bank_tran_id from payment_attempt where rent_payment_id = ? and attempt_no = 1",
			String.class, fixture.rentId())).isEqualTo(original);
		assertThat(jdbc.queryForObject("select fintech_use_num_snapshot from payment_attempt where bank_tran_id = ?",
			String.class, original)).isEqualTo(FINTECH_USE_NUM);
		assertThat(jdbc.queryForObject("select evidence_reference from collection_resolution", String.class)).isEqualTo("BANK-FAILED-001");
		assertThat(settlementStatus()).isEqualTo("COMPLETED");
		verify(client, times(2)).withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString());
		verify(client, times(1)).deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("지급 실패 확정 후에는 지급만 재시도하고 이전 계좌 스냅샷과 거래번호를 보존한다")
	void confirmedPayoutFailureRetriesOnlyPayoutAndPreservesAccountSnapshots() {
		registerSettlementAccount();
		when(client.deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString()))
			.thenReturn(new TransferResult(false, "999"), new TransferResult(true, "000"));
		rents.pay(fixture.rentId(), fixture.tenantId());
		String original = payoutBankTranId();
		assertThat(settlementStatus()).isEqualTo("UNKNOWN");
		assertError(ErrorCode.PAYMENT_IN_PROGRESS,
			() -> settlements.resolve(fixture.rentId(), "T000000001U999999999", false, "BANK-PAYOUT-001", fixture.adminId()));

		settlements.resolve(fixture.rentId(), original, false, "BANK-PAYOUT-001", fixture.adminId());

		assertThat(settlementStatus()).isEqualTo("FAILED");
		assertThat(rentStatus()).isEqualTo("PAID");
		String replacement = "00000000000002";
		settlements.registerAccount(fixture.landlordId(), "097", replacement, "테스트 임대인", "900101");
		settlements.settle(fixture.rentId());
		assertThat(settlementStatus()).isEqualTo("COMPLETED");
		assertThat(payoutBankTranId()).isNotEqualTo(original);
		assertThat(jdbc.queryForList("select attempt_status from settlement_attempt order by attempt_no", String.class))
			.containsExactly("FAILED", "SUCCEEDED");
		String firstAccount = jdbc.queryForObject("select account_number_encrypted_snapshot from settlement_attempt where bank_tran_id = ?",
			String.class, original);
		String secondAccount = jdbc.queryForObject("select account_number_encrypted_snapshot from settlement_attempt where bank_tran_id = ?",
			String.class, payoutBankTranId());
		assertThat(cipher.decrypt(firstAccount)).isEqualTo(ACCOUNT_NUMBER);
		assertThat(cipher.decrypt(secondAccount)).isEqualTo(replacement);
		assertThat(jdbc.queryForObject("select evidence_reference from settlement_resolution", String.class)).isEqualTo("BANK-PAYOUT-001");
		assertError(ErrorCode.PAYMENT_IN_PROGRESS,
			() -> settlements.resolve(fixture.rentId(), payoutBankTranId(), true, "DUPLICATE", fixture.adminId()));
		assertError(ErrorCode.PAYMENT_IN_PROGRESS,
			() -> settlements.resolve(fixture.rentId(), original, true, "OLD-TRANSACTION", fixture.adminId()));
		assertThat(jdbc.queryForObject("select count(*) from settlement_resolution", Long.class)).isEqualTo(1L);
		assertThat(attemptCount()).isEqualTo(1L);
		verify(client, times(1)).withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString());
		verify(client, times(2)).deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("10분이 지나지 않은 출금 PROCESSING은 확정할 수 없고 오래된 요청만 증빙 확정한다")
	void collectionResolutionRejectsFreshProcessingAndAllowsStaleJournal() {
		registerSettlementAccount();
		when(client.withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
			.thenReturn(new TransferResult(false, "999"));
		rents.pay(fixture.rentId(), fixture.tenantId());
		String original = collectionBankTranId();
		jdbc.update("update rent_payment set rent_payment_status = 'PROCESSING' where rent_payment_id = ?", fixture.rentId());
		jdbc.update("update payment_attempt set attempt_status = 'REQUESTED', requested_at = ? where bank_tran_id = ?",
			nowKorea(), original);

		assertError(ErrorCode.PAYMENT_IN_PROGRESS,
			() -> rents.resolveCollection(fixture.rentId(), original, true, "BANK-STALE-001", fixture.adminId()));
		assertThat(jdbc.queryForObject("select count(*) from collection_resolution", Long.class)).isZero();
		assertThat(rentStatus()).isEqualTo("PROCESSING");
		jdbc.update("update payment_attempt set requested_at = ? where bank_tran_id = ?", nowKorea().minusMinutes(11), original);
		rents.resolveCollection(fixture.rentId(), original, true, "BANK-STALE-001", fixture.adminId());
		assertThat(rentStatus()).isEqualTo("PAID");
		assertThat(settlementStatus()).isEqualTo("PENDING");
		verify(client, times(1)).withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString());
	}

	@Test
	@DisplayName("10분이 지나지 않은 지급 PROCESSING은 확정할 수 없고 오래된 요청만 증빙 확정한다")
	void payoutResolutionRejectsFreshProcessingAndAllowsStaleJournal() {
		registerSettlementAccount();
		when(client.deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString()))
			.thenReturn(new TransferResult(false, "999"));
		rents.pay(fixture.rentId(), fixture.tenantId());
		String original = payoutBankTranId();
		jdbc.update("update settlement set settlement_status = 'PROCESSING' where rent_payment_id = ?", fixture.rentId());
		jdbc.update("update settlement_attempt set attempt_status = 'REQUESTED', requested_at = ? where bank_tran_id = ?",
			nowKorea(), original);

		assertError(ErrorCode.PAYMENT_IN_PROGRESS,
			() -> settlements.resolve(fixture.rentId(), original, true, "BANK-STALE-PAYOUT-001", fixture.adminId()));
		assertThat(jdbc.queryForObject("select count(*) from settlement_resolution", Long.class)).isZero();
		assertThat(settlementStatus()).isEqualTo("PROCESSING");
		jdbc.update("update settlement_attempt set requested_at = ? where bank_tran_id = ?", nowKorea().minusMinutes(11), original);
		settlements.resolve(fixture.rentId(), original, true, "BANK-STALE-PAYOUT-001", fixture.adminId());
		assertThat(settlementStatus()).isEqualTo("COMPLETED");
		assertThat(rentStatus()).isEqualTo("PAID");
		verify(client, times(1)).withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString());
		verify(client, times(1)).deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("JVM 기본 시간대가 UTC여도 새 출금 요청을 10분 지난 거래로 오인하지 않는다")
	void freshCollectionCannotBeResolvedWhenJvmUsesUtc() {
		var originalZone = java.util.TimeZone.getDefault();
		try {
			java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
			registerSettlementAccount();
			when(client.withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
				.thenAnswer(invocation -> {
					var requestedAt = jdbc.queryForObject("select requested_at from payment_attempt where rent_payment_id = ?",
						LocalDateTime.class, fixture.rentId());
					assertThat(requestedAt).isAfter(nowKorea().minusMinutes(1));
					assertError(ErrorCode.PAYMENT_IN_PROGRESS, () -> rents.resolveCollection(fixture.rentId(),
						collectionBankTranId(), false, "STILL-IN-FLIGHT", fixture.adminId()));
					return new TransferResult(false, "999");
				});
			rents.pay(fixture.rentId(), fixture.tenantId());
			assertThat(rentStatus()).isEqualTo("UNKNOWN");
			assertThat(jdbc.queryForObject("select count(*) from collection_resolution", Long.class)).isZero();
		} finally {
			java.util.TimeZone.setDefault(originalZone);
		}
	}

	private void assertError(ErrorCode expected, Runnable action) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(OpenBankingException.class,
			exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
	}

	private String collectionBankTranId() {
		return jdbc.queryForObject("select bank_tran_id from payment_attempt where rent_payment_id = ? order by attempt_no desc limit 1",
			String.class, fixture.rentId());
	}

	private String payoutBankTranId() {
		return jdbc.queryForObject("select bank_tran_id from settlement where rent_payment_id = ?", String.class, fixture.rentId());
	}

	private User user(String name, Role role) {
		User user = User.builder().email(UUID.randomUUID() + "@example.test").name(name)
			.phone("01000000000").role(role).build();
		entityManager.persist(user);
		return user;
	}

	private void registerSettlementAccount() {
		settlements.registerAccount(fixture.landlordId(), "097", ACCOUNT_NUMBER, "테스트 임대인", "900101");
		String encrypted = jdbc.queryForObject("select account_number_encrypted from settlement_account where landlord_id = ?",
			String.class, fixture.landlordId());
		assertThat(encrypted).doesNotContain(ACCOUNT_NUMBER);
		assertThat(cipher.decrypt(encrypted)).isEqualTo(ACCOUNT_NUMBER);
		assertThat(jdbc.queryForObject("select verified_at from settlement_account where landlord_id = ?",
			LocalDateTime.class, fixture.landlordId())).isNotNull();
	}

	private Object payConcurrently() {
		try {
			return rents.pay(fixture.rentId(), fixture.tenantId());
		} catch (OpenBankingException exception) {
			return exception.getErrorCode();
		}
	}

	private void assertWithdrawalHeld() {
		assertThat(rentStatus()).isEqualTo("UNKNOWN");
		assertThat(attemptStatus()).isEqualTo("UNKNOWN");
		assertThat(attemptCount()).isEqualTo(1L);
		assertThat(jdbc.queryForObject("select count(*) from settlement where rent_payment_id = ?", Long.class, fixture.rentId()))
			.isZero();
	}

	private String rentStatus() {
		return jdbc.queryForObject("select rent_payment_status from rent_payment where rent_payment_id = ?", String.class, fixture.rentId());
	}

	private String attemptStatus() {
		return jdbc.queryForObject("select attempt_status from payment_attempt where rent_payment_id = ? order by attempt_no desc limit 1",
			String.class, fixture.rentId());
	}

	private String settlementStatus() {
		return jdbc.queryForObject("select settlement_status from settlement where rent_payment_id = ?", String.class, fixture.rentId());
	}

	private long attemptCount() {
		return jdbc.queryForObject("select count(*) from payment_attempt where rent_payment_id = ?", Long.class, fixture.rentId());
	}

	private static LocalDateTime nowKorea() {
		return LocalDateTime.now(ZoneId.of("Asia/Seoul"));
	}

	private record Fixture(Long rentId, Long tenantId, Long landlordId, Long adminId) { }
}
