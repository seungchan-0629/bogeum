package com.bogeum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.contract.ContractRepository;
import com.bogeum.domain.contract.model.Contract;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.JwtUtil;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실제 Security·JWT·H2/JPA·MVC로 계약 관계와 목록 응답을 검증하며 외부 서비스를 호출하지 않는다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-landlord-contract-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "spring.jpa.open-in-view=false",
	"spring.sql.init.mode=never", "auth.cookie-secure=false",
	"solapi.api-key=TEST_UNUSED", "solapi.api-secret=TEST_UNUSED", "solapi.sender-number=01000000000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LandlordContractIntegrationTests {
	private static final String PATH = "/api/landlords/me/contracts";
	private static final String TENANT_PATH = "/api/tenants/me/contracts";

	@MockitoSpyBean
	ContractRepository contracts;

	private final MockMvc mvc;
	private final EntityManager em;
	private final JsonMapper mapper;
	private final JwtUtil jwt;
	private final TransactionTemplate tx;

	@Autowired
	LandlordContractIntegrationTests(MockMvc mvc, EntityManager em, JsonMapper mapper, JwtUtil jwt,
			PlatformTransactionManager manager) {
		this.mvc = mvc;
		this.em = em;
		this.mapper = mapper;
		this.jwt = jwt;
		this.tx = new TransactionTemplate(manager);
	}

	@Test
	@DisplayName("인증된 임대인의 계약만 조회하고 사용자 번호 위조와 타인·임차인 계약 혼입을 막는다")
	void readsOnlyAuthenticatedLandlordContracts() throws Exception {
		var f = fixture();
		var result = mvc.perform(get(PATH).header("Authorization", bearer(f.landlord()))
			.param("userId", f.other().getUserId().toString()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[0].contractId").value(f.sameDate().getContractId()))
			.andExpect(jsonPath("$[0].propertyId").value(f.property().getPropertyId()))
			.andExpect(jsonPath("$[0].roadAddress").value("서울 계약로 114"))
			.andExpect(jsonPath("$[0].detailAddress").value("101호"))
			.andExpect(jsonPath("$[0].contractedAt").value("2026-11-10"))
			.andReturn();
		var body = mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
		assertThat(ids(body)).containsExactly(f.sameDate().getContractId(), f.recent().getContractId(), f.older().getContractId());
		// 다섯 필드를 모두 확인한 뒤 개수도 제한해 향후 개인정보 필드의 추가 노출을 막는다.
		for (var item : body) {
			assertThat(item.size()).isEqualTo(5);
		}
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
			.doesNotContain("tenantName", "landlordName", "email", "phone", "password", "userId");
	}

	@Test
	@DisplayName("같은 매물의 종료 계약도 포함하고 계약일 다음 계약 ID 내림차순으로 정렬한다")
	void sortsByContractDateThenIdIncludingEndedContracts() throws Exception {
		var f = fixture();
		assertThat(f.older().getEndAt()).isBefore(LocalDate.now().atStartOfDay());
		// 가장 나중에 저장한 과거 계약의 ID가 커도 계약일이 우선이다.
		assertThat(f.older().getContractId()).isGreaterThan(f.sameDate().getContractId());
		for (int attempt = 0; attempt < 2; attempt++) {
			var body = read(PATH, f.landlord());
			assertThat(ids(body)).containsExactly(f.sameDate().getContractId(), f.recent().getContractId(), f.older().getContractId());
			for (var item : body) {
				assertThat(item.get("propertyId").asLong()).isEqualTo(f.property().getPropertyId());
			}
			assertThat(body.get(2).get("contractedAt").asText()).isEqualTo("2020-01-01");
		}
	}

	@Test
	@DisplayName("현재 매물 소유자도 다른 사람이 임대인인 계약을 받지 않고 실제 계약 관계만 따른다")
	void ignoresCurrentPropertyOwnership() throws Exception {
		var f = fixture();
		assertThat(f.property().getLandlord().getUserId()).isEqualTo(f.other().getUserId());
		assertThat(f.recent().getLandlord().getUserId()).isEqualTo(f.landlord().getUserId());
		assertThat(ids(read(PATH, f.landlord())))
			.containsExactly(f.sameDate().getContractId(), f.recent().getContractId(), f.older().getContractId());
		assertThat(ids(read(PATH, f.other())))
			.containsExactly(f.unrelated().getContractId(), f.tenantOnly().getContractId());
	}

	@Test
	@DisplayName("계약이 없는 USER에게 다른 사용자의 계약 대신 빈 배열을 반환한다")
	void returnsEmptyForUserWithoutContracts() throws Exception {
		fixture();
		var user = tx.execute(status -> user(Role.USER));
		assertThat(read(PATH, user).isEmpty()).isTrue();
	}

	@Test
	@DisplayName("임차인으로만 계약에 참여한 USER의 임대인 목록은 비어 있다")
	void excludesTenantOnlyParticipation() throws Exception {
		var f = fixture();
		assertThat(read(PATH, f.tenant()).isEmpty()).isTrue();
	}

	@Test
	@DisplayName("페이지나 개수 상한 없이 본인의 모든 계약을 반환한다")
	void returnsAllContractsWithoutPaging() throws Exception {
		var owner = tx.execute(status -> {
			var landlord = user(Role.USER);
			var tenant = user(Role.USER);
			var agent = user(Role.AGENT);
			var property = property(landlord, agent, null);
			for (int index = 0; index < 12; index++) {
				contract(property, landlord, tenant, agent, "2026-11-10", false);
			}
			return landlord;
		});
		assertThat(read(PATH, owner).size()).isEqualTo(12);
	}

	@Test
	@DisplayName("상세주소가 없는 매물도 null 상세주소를 포함한 다섯 필드로 반환한다")
	void preservesNullDetailAddress() throws Exception {
		var owner = tx.execute(status -> {
			var landlord = user(Role.USER);
			var tenant = user(Role.USER);
			var agent = user(Role.AGENT);
			contract(property(landlord, agent, null), landlord, tenant, agent, "2026-11-10", false);
			return landlord;
		});
		var body = read(PATH, owner);
		assertThat(body.size()).isEqualTo(1);
		assertThat(body.get(0).has("detailAddress")).isTrue();
		assertThat(body.get(0).get("detailAddress").isNull()).isTrue();
		assertThat(body.get(0).size()).isEqualTo(5);
	}

	@ParameterizedTest
	@CsvSource({"ADMIN, GET", "ADMIN, HEAD", "AGENT, GET", "AGENT, HEAD"})
	@DisplayName("ADMIN·AGENT는 계약 임대인이어도 GET·HEAD 모두 조회 전에 403으로 거절한다")
	void rejectsNonUserRolesBeforeQuerying(Role role, String method) throws Exception {
		var owner = tx.execute(status -> {
			var landlord = user(role);
			var tenant = user(Role.USER);
			var agent = user(Role.AGENT);
			contract(property(landlord, agent, "101호"), landlord, tenant, agent, "2026-11-10", false);
			return landlord;
		});
		clearInvocations(contracts);
		mvc.perform(request(HttpMethod.valueOf(method), PATH).header("Authorization", bearer(owner)))
			.andExpect(status().isForbidden());
		verifyNoInteractions(contracts);
	}

	@ParameterizedTest
	@ValueSource(strings = {"GET", "HEAD"})
	@DisplayName("미로그인 GET·HEAD 모두 계약 조회 전에 401로 거절한다")
	void requiresAuthenticationBeforeQuerying(String method) throws Exception {
		clearInvocations(contracts);
		mvc.perform(request(HttpMethod.valueOf(method), PATH)).andExpect(status().isUnauthorized());
		verifyNoInteractions(contracts);
	}

	@Test
	@DisplayName("임대인 USER의 HEAD도 실제 MVC 조회로 연결하고 인증된 사용자 ID를 사용한다")
	void allowsLandlordHeadRequest() throws Exception {
		var f = fixture();
		clearInvocations(contracts);
		mvc.perform(request(HttpMethod.HEAD, PATH).header("Authorization", bearer(f.landlord()))
			.param("userId", f.other().getUserId().toString()))
			.andExpect(status().isOk());
		verify(contracts).findAllByLandlord_UserIdOrderByContractedAtDescContractIdDesc(f.landlord().getUserId());
	}

	@Test
	@DisplayName("기존 임차인 목록은 임차인 관계와 열 개의 기존 응답 필드·거래 조건을 유지한다")
	void preservesTenantContractResponseAndScope() throws Exception {
		var f = fixture();
		var result = mvc.perform(get(TENANT_PATH).header("Authorization", bearer(f.landlord())))
			.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].contractId").value(f.tenantOnly().getContractId()))
			.andExpect(jsonPath("$[0].propertyId").value(f.tenantOnly().getProperty().getPropertyId()))
			.andExpect(jsonPath("$[0].roadAddress").value("서울 계약로 114"))
			.andExpect(jsonPath("$[0].detailAddress").value("202호"))
			.andExpect(jsonPath("$[0].leaseType").value("MONTHLY"))
			.andExpect(jsonPath("$[0].deposit").value(10000000))
			.andExpect(jsonPath("$[0].monthlyRent").value(500000))
			.andExpect(jsonPath("$[0].paymentDay").value(10))
			.andExpect(jsonPath("$[0].startAt").value(f.tenantOnly().getStartAt().toLocalDate().toString()))
			.andExpect(jsonPath("$[0].endAt").value(f.tenantOnly().getEndAt().toLocalDate().toString()))
			.andReturn();
		assertThat(mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get(0).size()).isEqualTo(10);
	}

	@Test
	@DisplayName("기존 임차인 목록도 계약이 없으면 빈 배열을 유지한다")
	void preservesEmptyTenantContractResponse() throws Exception {
		var owner = tx.execute(status -> user(Role.USER));
		assertThat(read(TENANT_PATH, owner).isEmpty()).isTrue();
	}

	private JsonNode read(String path, User user) throws Exception {
		var result = mvc.perform(get(path).header("Authorization", bearer(user))).andExpect(status().isOk()).andReturn();
		var body = mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
		assertThat(body.isArray()).isTrue();
		return body;
	}

	private List<Long> ids(JsonNode body) {
		var ids = new ArrayList<Long>();
		body.forEach(item -> ids.add(item.get("contractId").asLong()));
		return ids;
	}

	private String bearer(User user) {
		return "Bearer " + jwt.createAccess(user.getUserId(), UUID.randomUUID().toString());
	}

	private Fixture fixture() {
		return tx.execute(status -> {
			var landlord = user(Role.USER);
			var tenant = user(Role.USER);
			var other = user(Role.USER);
			var agent = user(Role.AGENT);
			// 현재 소유 관계와 계약의 임대인을 일부러 다르게 둬 소유자로 조회하는 회귀를 잡는다.
			var property = property(other, agent, "101호");
			var recent = contract(property, landlord, tenant, agent, "2026-11-10", false);
			var sameDate = contract(property, landlord, tenant, agent, "2026-11-10", false);
			var older = contract(property, landlord, tenant, agent, "2020-01-01", true);
			var otherProperty = property(landlord, agent, "202호");
			var tenantOnly = contract(otherProperty, other, landlord, agent, "2026-11-12", false);
			var unrelated = contract(otherProperty, other, tenant, agent, "2026-11-13", false);
			em.flush();
			return new Fixture(landlord, tenant, other, property, recent, sameDate, older, tenantOnly, unrelated);
		});
	}

	private User user(Role role) {
		var user = User.builder().email(UUID.randomUUID() + "@example.test").passwordHash("test-unused")
			.name("계약 목록 테스트").phone("01000000000").role(role).build();
		em.persist(user);
		return user;
	}

	private Property property(User landlord, User agent, String detailAddress) {
		var property = Property.builder().landlord(landlord).agent(agent).leaseType(LeaseType.MONTHLY)
			.deposit(new BigDecimal("10000000")).monthlyRent(new BigDecimal("500000"))
			.roadAddress("서울 계약로 114").detailAddress(detailAddress).build();
		em.persist(property);
		return property;
	}

	private Contract contract(Property property, User landlord, User tenant, User agent, String contractedAt, boolean ended) {
		var startAt = ended ? LocalDate.of(2020, 2, 1) : LocalDate.now().plusMonths(1);
		var contract = Contract.builder().property(property).landlord(landlord).tenant(tenant).agent(agent)
			.leaseType(LeaseType.MONTHLY).deposit(new BigDecimal("10000000")).monthlyRent(new BigDecimal("500000"))
			.paymentDay(10).brokerageFee(new BigDecimal("300000"))
			.startAt(startAt.atStartOfDay()).endAt(startAt.plusYears(2).atStartOfDay())
			.contractedAt(LocalDate.parse(contractedAt).atStartOfDay()).build();
		em.persist(contract);
		return contract;
	}

	private record Fixture(User landlord, User tenant, User other, Property property, Contract recent,
		Contract sameDate, Contract older, Contract tenantOnly, Contract unrelated) {}
}
