package com.bogeum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PaymentMethodType;
import com.bogeum.common.enums.RentPaymentStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.contract.model.Contract;
import com.bogeum.domain.openbanking.OpenBankingException;
import com.bogeum.domain.openbanking.service.OpenBankingService;
import com.bogeum.domain.openbanking.support.OpenBankingClient;
import com.bogeum.domain.openbanking.support.OpenBankingClient.Token;
import com.bogeum.domain.openbanking.support.OpenBankingClient.TransferResult;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.rent.RentService;
import com.bogeum.domain.rent.model.PaymentMethod;
import com.bogeum.domain.rent.model.RentPayment;
import com.bogeum.domain.rent.service.RentTransferService;
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
	private final RentTransferService rentTransfers;
	private final SettlementService settlements;
	private final EntityManager entityManager;
	private final JdbcTemplate jdbc;
	private final PlatformTransactionManager transactionManager;
	private final SecretCipher cipher;

	@MockitoBean private OpenBankingClient client;
	@MockitoBean private OpenBankingService openBanking;

	private Fixture fixture;

	@Autowired
	OpenBankingTransferTests(RentService rents, RentTransferService rentTransfers, SettlementService settlements,
			EntityManager entityManager, JdbcTemplate jdbc, PlatformTransactionManager transactionManager, SecretCipher cipher) {
		this.rents = rents;
		this.rentTransfers = rentTransfers;
		this.settlements = settlements;
		this.entityManager = entityManager;
		this.jdbc = jdbc;
		this.transactionManager = transactionManager;
		this.cipher = cipher;
	}

	@BeforeEach
	void prepareContractAndMockBank() {
		for (String table : List.of("settlement", "settlement_account", "payment_attempt", "rent_payment", "payment_method",
				"contract", "property", "user_consent", "user")) {
			jdbc.update("delete from " + table);
		}
		var sequence = new AtomicInteger();
		when(client.newBankTranId()).thenAnswer(invocation -> "T000000001U%09d".formatted(sequence.incrementAndGet()));
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
			Property property = Property.builder().landlord(landlord).agent(agent).leaseType(LeaseType.MONTHLY)
				.deposit(new BigDecimal("10000000")).monthlyRent(AMOUNT).roadAddress("테스트 주소").build();
			entityManager.persist(property);
			Contract contract = Contract.builder().property(property).landlord(landlord).tenant(tenant).agent(agent)
				.leaseType(LeaseType.MONTHLY).deposit(property.getDeposit()).monthlyRent(AMOUNT).paymentDay(1)
				.startAt(LocalDateTime.now().minusMonths(1)).endAt(LocalDateTime.now().plusYears(1)).build();
			entityManager.persist(contract);
			entityManager.persist(PaymentMethod.builder().tenant(tenant).methodType(PaymentMethodType.ACCOUNT)
				.fintechUseNum(FINTECH_USE_NUM).bankCode("097").accountNumberMasked("000-***")
				.withdrawalConsentedAt(LocalDateTime.now().minusDays(1)).build());
			RentPayment rent = RentPayment.builder().contract(contract).seq(1).amount(AMOUNT)
				.dueAt(LocalDateTime.now().minusHours(1)).build();
			entityManager.persist(rent);
			return new Fixture(rent.getRentPaymentId(), tenant.getUserId(), landlord.getUserId());
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
	@DisplayName("정산 계좌가 없으면 수납을 유지하고 지급은 PENDING으로 보류한다")
	void missingSettlementAccountKeepsCollectedMoneyPending() {
		rents.pay(fixture.rentId(), fixture.tenantId());
		assertThat(rentStatus()).isEqualTo("PAID");
		assertThat(settlementStatus()).isEqualTo("PENDING");
		assertThat(jdbc.queryForObject("select bank_tran_id from settlement where rent_payment_id = ?", String.class, fixture.rentId()))
			.isNull();
		verify(client, never()).deposit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString());
		registerSettlementAccount();
		settlements.settle(fixture.rentId());
		assertThat(settlementStatus()).isEqualTo("COMPLETED");
		verify(client, times(1)).withdraw(anyString(), anyString(), anyString(), any(), anyString(), anyString());
	}

	@Test
	@DisplayName("과거 PG 성공 기록을 새 오픈뱅킹 수납으로 간주하여 지급하지 않는다")
	void legacyPgCollectionCannotFundOpenBankingPayout() {
		rents.pay(fixture.rentId(), fixture.tenantId());
		// 기존 PG 성공 이력은 새 오픈뱅킹 은행거래고유번호를 갖지 않는다.
		jdbc.update("update payment_attempt set bank_tran_id = null where rent_payment_id = ?", fixture.rentId());
		registerSettlementAccount();
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
	@DisplayName("동시에 수납을 준비해도 출금 요청 저널은 하나만 커밋된다")
	void concurrentCollectionPreparationCreatesOneAttempt() throws Exception {
		var executor = Executors.newFixedThreadPool(2);
		var start = new CountDownLatch(1);
		try {
			var first = executor.submit(() -> prepareConcurrently(start, "T000000001U000000001"));
			var second = executor.submit(() -> prepareConcurrently(start, "T000000001U000000002"));
			start.countDown();
			List<Object> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
			assertThat(results.stream().filter(RentTransferService.Withdrawal.class::isInstance).count()).isEqualTo(1);
			assertThat(results.stream().filter(ErrorCode.PAYMENT_IN_PROGRESS::equals).count()).isEqualTo(1);
			assertThat(attemptCount()).isEqualTo(1L);
			assertThat(rentStatus()).isEqualTo("PROCESSING");
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("상위 작업이 롤백돼도 이미 커밋한 출금 저널은 남아 재출금을 차단한다")
	void collectionJournalSurvivesCallerRollback() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			rentTransfers.prepare(fixture.rentId(), fixture.tenantId(), "T000000001U000000001");
			status.setRollbackOnly();
		});
		assertThat(rentStatus()).isEqualTo("PROCESSING");
		assertThat(attemptStatus()).isEqualTo("REQUESTED");
		assertThatThrownBy(() -> rentTransfers.prepare(fixture.rentId(), fixture.tenantId(), "T000000001U000000002"))
			.isInstanceOf(OpenBankingException.class);
		assertThat(attemptCount()).isEqualTo(1L);
	}

	private User user(String name, Role role) {
		User user = User.builder().email(UUID.randomUUID() + "@example.test").name(name)
			.phone("01000000000").role(role).build();
		entityManager.persist(user);
		return user;
	}

	private void registerSettlementAccount() {
		settlements.registerAccount(fixture.landlordId(), "097", ACCOUNT_NUMBER, "테스트 임대인");
		String encrypted = jdbc.queryForObject("select account_number_encrypted from settlement_account where landlord_id = ?",
			String.class, fixture.landlordId());
		assertThat(encrypted).doesNotContain(ACCOUNT_NUMBER);
		assertThat(cipher.decrypt(encrypted)).isEqualTo(ACCOUNT_NUMBER);
	}

	private Object prepareConcurrently(CountDownLatch start, String bankTranId) throws InterruptedException {
		start.await();
		try {
			return rentTransfers.prepare(fixture.rentId(), fixture.tenantId(), bankTranId);
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
		return jdbc.queryForObject("select attempt_status from payment_attempt where rent_payment_id = ?", String.class, fixture.rentId());
	}

	private String settlementStatus() {
		return jdbc.queryForObject("select settlement_status from settlement where rent_payment_id = ?", String.class, fixture.rentId());
	}

	private long attemptCount() {
		return jdbc.queryForObject("select count(*) from payment_attempt where rent_payment_id = ?", Long.class, fixture.rentId());
	}

	private record Fixture(Long rentId, Long tenantId, Long landlordId) { }
}
