package com.bogeum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.RentPaymentStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.contract.model.Contract;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.rent.RentRepository;
import com.bogeum.domain.rent.RentService;
import com.bogeum.domain.rent.model.RentDto;
import com.bogeum.domain.rent.model.RentPayment;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.JwtUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/** 실제 JWT 필터와 H2 쿼리를 거쳐 관리자 납부 조회 계약을 검증한다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-admin-rent-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
	"spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.generate_statistics=true",
	"auth.cookie-secure=false",
	"solapi.api-key=TEST_UNUSED", "solapi.api-secret=TEST_UNUSED", "solapi.sender-number=01000000000"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AdminRentPaymentReadTests {
	private static final String PATH = "/admin/rents/payments";
	private static final LocalDateTime DUE_AT = LocalDateTime.of(2026, 11, 10, 9, 0);
	@Autowired private MockMvc mvc;
	@Autowired private RentRepository rents;
	@Autowired private RentService service;
	@Autowired private UserRepository users;
	@Autowired private JwtUtil jwt;
	@Autowired private EntityManager em;
	@Autowired private EntityManagerFactory emf;

	@ParameterizedTest
	@EnumSource(RentPaymentStatus.class)
	void mapsStoredAmountDatesAndEveryStatusWithoutSensitiveFields(RentPaymentStatus state) throws Exception {
		var tenant = user("임차인", Role.USER);
		var contract = contract(tenant, "서울시 보금로 38", "101호");
		var paidAt = state == RentPaymentStatus.PAID ? DUE_AT.minusHours(1) : null;
		var payment = payment(contract, 3, DUE_AT, state, paidAt);
		em.clear();
		var result = mvc.perform(get(PATH).header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.payments.length()").value(1))
			.andExpect(jsonPath("$.payments[0].rentPaymentId").value(payment.getRentPaymentId()))
			.andExpect(jsonPath("$.payments[0].contractId").value(contract.getContractId()))
			.andExpect(jsonPath("$.payments[0].tenantId").value(tenant.getUserId()))
			.andExpect(jsonPath("$.payments[0].tenantName").value("임차인"))
			.andExpect(jsonPath("$.payments[0].propertyId").value(contract.getProperty().getPropertyId()))
			.andExpect(jsonPath("$.payments[0].roadAddress").value("서울시 보금로 38"))
			.andExpect(jsonPath("$.payments[0].detailAddress").value("101호"))
			.andExpect(jsonPath("$.payments[0].seq").value(3))
			.andExpect(jsonPath("$.payments[0].amount").value(123456789012345L))
			.andExpect(jsonPath("$.payments[0].dueAt").value("2026-11-10T09:00:00"))
			.andExpect(jsonPath("$.payments[0].rentPaymentStatus").value(state.name()));
		if (paidAt == null) {
			result.andExpect(jsonPath("$.payments[0].paidAt").value(org.hamcrest.Matchers.nullValue()));
		} else {
			result.andExpect(jsonPath("$.payments[0].paidAt").value("2026-11-10T08:00:00"));
		}
		result.andExpect(jsonPath("$.payments[0].length()").value(12))
			.andExpect(jsonPath("$.payments[0].passwordHash").doesNotExist())
			.andExpect(jsonPath("$.payments[0].email").doesNotExist())
			.andExpect(jsonPath("$.payments[0].phone").doesNotExist())
			.andExpect(jsonPath("$.payments[0].paymentMethod").doesNotExist())
			.andExpect(jsonPath("$.payments[0].pgTransactionId").doesNotExist())
			.andExpect(jsonPath("$.payments[0].retryCount").doesNotExist());
	}

	@Test
	void filtersByContractTenantAcrossContractsRegardlessOfAccountRole() throws Exception {
		var tenant = user("보금 tenant", Role.AGENT);
		var first = contract(tenant, "주소 1", null);
		var second = contract(tenant, "주소 2", "202호");
		payment(first, 1, DUE_AT, RentPaymentStatus.PAID, DUE_AT);
		payment(second, 1, DUE_AT.plusDays(1), RentPaymentStatus.SCHEDULED, null);
		// 이름이 같은 다른 임차인의 내역과, 대상이 임대인으로만 참여하는 계약은 제외한다.
		var other = contract(user("보금 tenant", Role.USER), "주소 3", null, tenant);
		payment(other, 1, DUE_AT.plusDays(2), RentPaymentStatus.FAILED, null);
		em.clear();
		mvc.perform(get(PATH).param("tenantId", tenant.getUserId().toString()).param("keyword", " TeNaNt ")
			.header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.payments[0].contractId").value(second.getContractId()))
			.andExpect(jsonPath("$.payments[1].contractId").value(first.getContractId()));
		mvc.perform(get(PATH).param("tenantId", tenant.getUserId().toString()).param("keyword", "불일치")
			.header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.payments").isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"%", "_", "%_"})
	void searchesTenantNameLiterallyRatherThanEmailOrAddress(String keyword) throws Exception {
		payment(contract(user("100%_Bogeum", Role.USER), "다른 주소", null), 1, DUE_AT,
			RentPaymentStatus.PAID, DUE_AT);
		payment(contract(user("일반 이름", Role.USER), "100%_주소", null), 1, DUE_AT,
			RentPaymentStatus.FAILED, null);
		mvc.perform(get(PATH).param("keyword", keyword).header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.payments[0].tenantName").value("100%_Bogeum"));
		mvc.perform(get(PATH).param("keyword", "example.com").header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.payments").isEmpty());
	}

	@Test
	void paginatesMixedContractsWithStableDueDateAndIdOrdering() throws Exception {
		var target = user("대상", Role.USER);
		var first = contract(target, "주소 1", null);
		var second = contract(target, "주소 2", null);
		var other = contract(user("다른 임차인", Role.USER), "주소 3", null);
		var expected = new java.util.ArrayList<Long>();
		for (int i = 0; i < 7; i++) {
			expected.add(payment(i % 2 == 0 ? first : second, i + 1, DUE_AT,
				RentPaymentStatus.SCHEDULED, null).getRentPaymentId());
		}
		payment(other, 1, DUE_AT.plusDays(10), RentPaymentStatus.OVERDUE, null);
		var older = payment(first, 99, DUE_AT.minusDays(1), RentPaymentStatus.PAID, DUE_AT);
		String admin = token(Role.ADMIN);
		em.clear();
		mvc.perform(get(PATH).param("tenantId", target.getUserId().toString()).header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.payments.length()").value(5))
			.andExpect(jsonPath("$.payments[0].rentPaymentId").value(expected.get(6)))
			.andExpect(jsonPath("$.payments[4].rentPaymentId").value(expected.get(2)))
			.andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.totalElements").value(8));
		mvc.perform(get(PATH).param("tenantId", target.getUserId().toString()).param("page", "2")
			.header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.payments.length()").value(3))
			.andExpect(jsonPath("$.payments[0].rentPaymentId").value(expected.get(1)))
			.andExpect(jsonPath("$.payments[2].rentPaymentId").value(older.getRentPaymentId()))
			.andExpect(jsonPath("$.page").value(2)).andExpect(jsonPath("$.totalElements").value(8));
		mvc.perform(get(PATH).param("tenantId", target.getUserId().toString()).param("page", "3")
			.header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.payments").isEmpty())
			.andExpect(jsonPath("$.page").value(3)).andExpect(jsonPath("$.totalElements").value(8));
	}

	@Test
	void fetchesPageAndCountWithoutNPlusOneAfterEntitiesAreDetached() {
		for (int i = 0; i < 6; i++) {
			payment(contract(user("임차인 " + i, Role.USER), "주소 " + i, null), 1, DUE_AT,
				RentPaymentStatus.PAID, DUE_AT);
		}
		em.flush();
		em.clear();
		var statistics = emf.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		var page = service.readAdminPayments(null, null, 1);
		em.clear();
		var response = RentDto.PaymentListRes.from(page);
		assertThat(response.getPayments()).hasSize(5);
		assertThat(response.getTotalElements()).isEqualTo(6);
		assertThat(response.getPayments()).allSatisfy(item -> {
			assertThat(item.getTenantName()).startsWith("임차인 ");
			assertThat(item.getRoadAddress()).startsWith("주소 ");
		});
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
	}

	@Test
	void returnsEmptyMetadataForNoRowsMissingTenantAndBlankKeyword() throws Exception {
		String admin = token(Role.ADMIN);
		mvc.perform(get(PATH).param("keyword", "   ").header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.payments").isEmpty())
			.andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.totalPages").value(0))
			.andExpect(jsonPath("$.totalElements").value(0));
		payment(contract(user("대상", Role.USER), "주소", null), 1, DUE_AT, RentPaymentStatus.PAID, DUE_AT);
		mvc.perform(get(PATH).param("tenantId", "9223372036854775807").header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-1", "-2147483648"})
	void normalizesPagesBelowOne(String page) throws Exception {
		mvc.perform(get(PATH).param("page", page).header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1));
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "1.5", "429496731", "2147483647", "2147483648", "-2147483649"})
	void rejectsInvalidOrOverflowingPages(String page) throws Exception {
		mvc.perform(get(PATH).param("page", page).header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void acceptsLastPageWithinJpaOffsetRange() throws Exception {
		payment(contract(user("대상", Role.USER), "주소", null), 1, DUE_AT, RentPaymentStatus.PAID, DUE_AT);
		mvc.perform(get(PATH).param("page", "429496730").header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.payments").isEmpty())
			.andExpect(jsonPath("$.page").value(429496730)).andExpect(jsonPath("$.totalElements").value(1));
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-1", "abc", "1.5", "9223372036854775808"})
	void rejectsInvalidTenantId(String tenantId) throws Exception {
		mvc.perform(get(PATH).param("tenantId", tenantId).header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@ParameterizedTest
	@EnumSource(Role.class)
	void appliesAdminAuthorizationToGetAndHead(Role role) throws Exception {
		String authorization = token(role);
		int expected = role == Role.ADMIN ? 200 : 403;
		mvc.perform(get(PATH).header("Authorization", authorization)).andExpect(status().is(expected));
		mvc.perform(head(PATH).header("Authorization", authorization)).andExpect(status().is(expected));
	}

	@Test
	void rejectsUnauthenticatedInvalidAndWrongPurposeTokens() throws Exception {
		mvc.perform(get(PATH)).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		mvc.perform(head(PATH)).andExpect(status().isUnauthorized());
		mvc.perform(get(PATH).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("ACCESS_TOKEN_INVALID"));
		var user = user("관리자", Role.ADMIN);
		String refresh = jwt.createRefresh(user.getUserId(), UUID.randomUUID().toString(),
			java.time.Instant.now().plusSeconds(60));
		mvc.perform(get(PATH).header("Authorization", "Bearer " + refresh))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCESS_TOKEN_INVALID"));
	}

	private String token(Role role) {
		return "Bearer " + jwt.createAccess(user("인증 사용자", role).getUserId(), UUID.randomUUID().toString());
	}

	private User user(String name, Role role) {
		return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
			.passwordHash("must-not-be-exposed").name(name).phone("01000000000").role(role).build());
	}

	private Contract contract(User tenant, String roadAddress, String detailAddress) {
		return contract(tenant, roadAddress, detailAddress, user("임대인", Role.USER));
	}

	private Contract contract(User tenant, String roadAddress, String detailAddress, User landlord) {
		var agent = user("중개사", Role.AGENT);
		var property = Property.builder().landlord(landlord).agent(agent).leaseType(LeaseType.MONTHLY)
			.deposit(BigDecimal.TEN).monthlyRent(BigDecimal.valueOf(500000))
			.roadAddress(roadAddress).detailAddress(detailAddress).build();
		em.persist(property);
		var contract = Contract.builder().property(property).tenant(tenant).landlord(landlord).agent(agent)
			.leaseType(LeaseType.MONTHLY).deposit(BigDecimal.TEN).monthlyRent(BigDecimal.valueOf(500000))
			.paymentDay(10).startAt(DUE_AT.minusMonths(3)).endAt(DUE_AT.plusMonths(9))
			.brokerageFee(BigDecimal.ONE).contractedAt(DUE_AT.minusMonths(4)).build();
		em.persist(contract);
		return contract;
	}

	private RentPayment payment(Contract contract, int seq, LocalDateTime dueAt,
			RentPaymentStatus status, LocalDateTime paidAt) {
		return rents.saveAndFlush(RentPayment.builder().contract(contract).seq(seq).dueAt(dueAt)
			.amount(new BigDecimal("123456789012345")).rentPaymentStatus(status).paidAt(paidAt).build());
	}
}
