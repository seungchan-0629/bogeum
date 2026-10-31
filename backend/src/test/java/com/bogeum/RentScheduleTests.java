package com.bogeum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import com.bogeum.common.enums.Role;
import com.bogeum.domain.contract.model.Contract;
import com.bogeum.domain.openbanking.OpenBankingClient;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.rent.RentService;
import com.bogeum.domain.rent.model.RentPayment;
import com.bogeum.domain.user.model.User;

import jakarta.persistence.EntityManager;

/** 독립된 H2 DB에서 계약 잠금과 회차 스냅샷 보존을 검증한다. 서버·실은행을 사용하지 않는다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-rent-schedule-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.jpa.show-sql=false",
	"spring.jpa.open-in-view=false",
	"spring.sql.init.mode=never",
	"openbanking.enabled=false",
	"openbanking.scheduling-enabled=false",
	"openbanking.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@ActiveProfiles("test")
class RentScheduleTests {

	@Autowired private RentService rents;
	@Autowired private EntityManager entityManager;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private JdbcTemplate jdbc;
	@MockitoBean private OpenBankingClient client;

	@Test
	@DisplayName("동시 회차 생성 요청은 계약 잠금 안에서 같은 회차 3개만 생성한다")
	void concurrentGenerationCreatesOneSchedule() throws Exception {
		Long contractId = createContract();
		var start = new CountDownLatch(1);
		var workers = Executors.newFixedThreadPool(4);
		List<Future<List<Long>>> results = new ArrayList<>();
		try {
			for (int i = 0; i < 4; i++) {
				results.add(workers.submit(() -> {
					if (!start.await(10, TimeUnit.SECONDS)) {
						throw new IllegalStateException("회차 생성 시작 대기 시간 초과");
					}
					return rents.generatePayments(contractId).stream().map(RentPayment::getRentPaymentId).toList();
				}));
			}
			start.countDown();
			List<Long> expected = results.get(0).get(20, TimeUnit.SECONDS);
			assertThat(expected).hasSize(3);
			for (var result : results) {
				assertThat(result.get(20, TimeUnit.SECONDS)).containsExactlyElementsOf(expected);
			}
			assertThat(jdbc.queryForObject("select count(*) from rent_payment where contract_id = ?", Long.class, contractId))
				.isEqualTo(3L);
			verifyNoInteractions(client);
		} finally {
			start.countDown();
			workers.shutdownNow();
		}
	}

	@Test
	@DisplayName("계약 월세와 납부일 변경 후 다시 생성해도 기존 회차 금액과 기한은 유지한다")
	void regenerationPreservesSnapshots() {
		Long contractId = createContract();
		List<RentPayment> initial = rents.generatePayments(contractId);
		jdbc.update("update contract set monthly_rent = ?, payment_day = ? where contract_id = ?",
			new BigDecimal("900000"), 15, contractId);

		List<RentPayment> regenerated = rents.generatePayments(contractId);

		assertThat(regenerated).extracting(RentPayment::getRentPaymentId)
			.containsExactlyElementsOf(initial.stream().map(RentPayment::getRentPaymentId).toList());
		assertThat(regenerated).extracting(RentPayment::getAmount)
			.allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("650000"));
		assertThat(regenerated).extracting(RentPayment::getDueAt).containsExactly(
			LocalDateTime.of(2028, 1, 31, 9, 0), LocalDateTime.of(2028, 2, 29, 9, 0),
			LocalDateTime.of(2028, 3, 31, 9, 0));
		verifyNoInteractions(client);
	}

	private Long createContract() {
		return new TransactionTemplate(transactionManager).execute(status -> {
			User tenant = user("임차인", Role.USER);
			User landlord = user("임대인", Role.USER);
			User agent = user("중개사", Role.AGENT);
			Property property = Property.builder().landlord(landlord).agent(agent).leaseType(LeaseType.MONTHLY)
				.deposit(new BigDecimal("10000000")).monthlyRent(new BigDecimal("650000")).roadAddress("테스트 주소").build();
			entityManager.persist(property);
			Contract contract = Contract.builder().property(property).tenant(tenant).landlord(landlord).agent(agent)
				.leaseType(LeaseType.MONTHLY).deposit(property.getDeposit()).monthlyRent(property.getMonthlyRent()).paymentDay(31)
				.startAt(LocalDateTime.of(2028, 1, 1, 0, 0)).endAt(LocalDateTime.of(2028, 3, 31, 23, 59)).build();
			entityManager.persist(contract);
			return contract.getContractId();
		});
	}

	private User user(String name, Role role) {
		User user = User.builder().email(UUID.randomUUID() + "@example.com").name(name)
			.phone("01012345678").role(role).build();
		entityManager.persist(user);
		return user;
	}
}
