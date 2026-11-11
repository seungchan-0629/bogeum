package com.bogeum.domain.openbanking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.contract.model.Contract;
import com.bogeum.domain.openbanking.model.OpenBanking;
import com.bogeum.domain.openbanking.model.OpenBankingTransfer;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.JwtUtil;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

/** 실제 JWT·JPA 조회와 모의 금결원 응답으로 계약별 이체 저장·조회 범위를 검증한다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-transfer-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
	"spring.sql.init.mode=never", "auth.cookie-secure=false",
	"openbanking.client-id=test-institution-client", "openbanking.client-secret=test-institution-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OpenBankingTransferIntegrationTest {
	private static final String WITHDRAWALS = "/openbanking/transfers/withdrawals/me";
	private static final String REMITTANCES = "/openbanking/transfers/remittances/me";
	private final MockMvc mockMvc;
	private final EntityManager entityManager;
	private final UserRepository users;
	private final OpenBankingRepository accounts;
	private final OpenBankingTransferRepository transfers;
	private final OpenBankingService service;
	private final JwtUtil jwtUtil;
	private MockRestServiceServer bank;

	@Autowired
	OpenBankingTransferIntegrationTest(MockMvc mockMvc, EntityManager entityManager, UserRepository users,
			OpenBankingRepository accounts, OpenBankingTransferRepository transfers,
			OpenBankingService service, JwtUtil jwtUtil) {
		this.mockMvc = mockMvc;
		this.entityManager = entityManager;
		this.users = users;
		this.accounts = accounts;
		this.transfers = transfers;
		this.service = service;
		this.jwtUtil = jwtUtil;
	}

	@BeforeEach
	void mockBank() {
		var builder = RestClient.builder().baseUrl("http://localhost:8090");
		bank = MockRestServiceServer.bindTo(builder).build();
		Object target = AopTestUtils.getTargetObject(service);
		ReflectionTestUtils.setField(target, "restClient", builder.build());
	}

	@AfterEach
	void verifyBank() {
		bank.verify();
	}

	@Test
	@DisplayName("임차인·임대인을 겸하는 사용자도 각 계약 역할에 맞는 이체만 조회한다")
	void separatesContractRolesAndTransferTypes() throws Exception {
		User user = user(Role.USER);
		User other = user(Role.USER);
		Contract tenantContract = contract(other, user);
		Contract landlordContract = contract(user, other);
		Contract unrelated = contract(other, other);
		transfer(tenantContract, TransferType.WITHDRAW, "2026-11-01T09:00:00");
		transfer(tenantContract, TransferType.REMITTANCE, "2026-11-02T09:00:00");
		transfer(landlordContract, TransferType.WITHDRAW, "2026-11-01T09:00:00");
		transfer(landlordContract, TransferType.REMITTANCE, "2026-11-02T09:00:00");
		transfer(unrelated, TransferType.WITHDRAW, "2026-11-01T09:00:00");
		transfer(null, TransferType.WITHDRAW, "2026-11-01T09:00:00");
		entityManager.flush();
		entityManager.clear();

		mockMvc.perform(get(WITHDRAWALS).header("Authorization", bearer(user))
			.param("userId", other.getUserId().toString()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.transfers.length()").value(1))
			.andExpect(jsonPath("$.transfers[0].contractId").value(tenantContract.getContractId()))
			.andExpect(jsonPath("$.transfers[0].transferType").value("WITHDRAW"))
			.andExpect(jsonPath("$.transfers[0].amount").value(650000))
			.andExpect(jsonPath("$.transfers[0].roadAddress").value("서울 강남구 테스트로 1"))
			.andExpect(jsonPath("$.transfers[0].accessToken").doesNotExist())
			.andExpect(jsonPath("$.hasNext").value(false));
		mockMvc.perform(get(REMITTANCES).header("Authorization", bearer(user)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.transfers.length()").value(1))
			.andExpect(jsonPath("$.transfers[0].contractId").value(landlordContract.getContractId()))
			.andExpect(jsonPath("$.transfers[0].transferType").value("REMITTANCE"));
		mockMvc.perform(get(WITHDRAWALS).header("Authorization", bearer(user))
			.param("contractId", unrelated.getContractId().toString()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.transfers").isEmpty());
	}

	@ParameterizedTest
	@CsvSource({"WITHDRAW, /openbanking/transfers/withdrawals/me", "REMITTANCE, /openbanking/transfers/remittances/me"})
	@DisplayName("계약·매물·연월 필터와 다음 커서를 적용하고 월 경계를 넘는 이체는 제외한다")
	void filtersAndPaginates(TransferType type, String path) throws Exception {
		User user = user(Role.USER);
		Contract contract = contract(user, user);
		transfer(contract, type, "2026-10-31T23:59:59");
		var first = transfer(contract, type, "2026-11-01T00:00:00");
		var second = transfer(contract, type, "2026-11-30T23:59:59");
		transfer(contract, type, "2026-12-01T00:00:00");
		transfer(contract(user, user), type, "2026-11-15T09:00:00");

		mockMvc.perform(get(path).header("Authorization", bearer(user))
			.param("contractId", contract.getContractId().toString())
			.param("propertyId", contract.getProperty().getPropertyId().toString())
			.param("year", "2026").param("month", "11").param("size", "1"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.transfers.length()").value(1))
			.andExpect(jsonPath("$.transfers[0].openBankingTransferId").value(second.getOpenBankingTransferId()))
			.andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.nextCursor").value(second.getOpenBankingTransferId()));
		mockMvc.perform(get(path).header("Authorization", bearer(user))
			.param("contractId", contract.getContractId().toString())
			.param("year", "2026").param("month", "11").param("size", "1")
			.param("cursor", second.getOpenBankingTransferId().toString()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.transfers.length()").value(1))
			.andExpect(jsonPath("$.transfers[0].openBankingTransferId").value(first.getOpenBankingTransferId()))
			.andExpect(jsonPath("$.hasNext").value(false)).andExpect(jsonPath("$.nextCursor").isEmpty());
	}

	@Test
	@DisplayName("연도 필터는 연초·연말을 포함하고 이전·다음 연도는 제외한다")
	void filtersYearBoundaries() throws Exception {
		User user = user(Role.USER);
		Contract contract = contract(user, user);
		transfer(contract, TransferType.WITHDRAW, "2025-12-31T23:59:59");
		transfer(contract, TransferType.WITHDRAW, "2026-01-01T00:00:00");
		transfer(contract, TransferType.WITHDRAW, "2026-12-31T23:59:59");
		transfer(contract, TransferType.WITHDRAW, "2027-01-01T00:00:00");
		mockMvc.perform(get(WITHDRAWALS).header("Authorization", bearer(user)).param("year", "2026"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.transfers.length()").value(2));
	}

	@ParameterizedTest
	@CsvSource({"size,0", "size,101", "year,10000", "month,13", "month,1", "cursor,-1", "contractId,0", "propertyId,-1", "year,abc"})
	@DisplayName("잘못된 조회 조건은 공통 400 응답으로 거절한다")
	void rejectsInvalidFilters(String key, String value) throws Exception {
		mockMvc.perform(get(WITHDRAWALS).header("Authorization", bearer(user(Role.USER))).param(key, value))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("로그인 없이 두 이체 내역 API를 조회할 수 없다")
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get(WITHDRAWALS)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(REMITTANCES)).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("계약 당사자도 출금·송금과 기관 토큰 관리 API를 직접 호출할 수 없다")
	void rejectsNonAdminBankingRequests() throws Exception {
		User tenant = user(Role.USER);
		User landlord = user(Role.USER);
		Contract contract = contract(landlord, tenant);
		OpenBanking tenantAccount = account(tenant);
		OpenBanking landlordAccount = account(landlord);
		mockMvc.perform(csrfPost("/openbanking/accounts/" + tenantAccount.getOpenBankingId() + "/withdrawals", tenant)
			.header("X-Openbanking-Client-Id", "test").header("X-Openbanking-Client-Secret", "test")
			.header("X-Openbanking-Scope", "transfer")
			.content(withdrawalJson(contract.getContractId(), tenantAccount.getFintechNum())))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(remittancePost(landlord).content(remittanceJson(contract.getContractId(), landlordAccount.getFintechNum())))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(get("/openbanking/admin/institution-token").header("Authorization", bearer(tenant)))
			.andExpect(status().isForbidden());
		mockMvc.perform(csrfPost("/openbanking/admin/institution-token", tenant))
			.andExpect(status().isForbidden());
		assertEquals(0, transfers.count());
	}

	@Test
	@DisplayName("관리자는 기관 토큰을 발급·조회할 수 있고 응답에는 마스킹한 토큰만 포함한다")
	void managesInstitutionTokenWithoutExposingRawToken() throws Exception {
		User admin = user(Role.ADMIN);
		String path = "/openbanking/admin/institution-token";
		mockMvc.perform(get(path).header("Authorization", bearer(admin)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.issued").value(false));
		bank.expect(requestTo("http://localhost:8090/oauth/2.0/token"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("""
				{"access_token":"test-institution-access-token","token_type":"Bearer",
				 "expires_in":3600,"scope":"oob","client_use_code":"F123456789"}
				""", MediaType.APPLICATION_JSON));
		mockMvc.perform(csrfPost(path, admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.issued").value(true))
			.andExpect(jsonPath("$.usable").value(true))
			.andExpect(jsonPath("$.maskedToken").value("test••••••••oken"))
			.andExpect(jsonPath("$.accessToken").doesNotExist());
		mockMvc.perform(get(path).header("Authorization", bearer(admin)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.issued").value(true))
			.andExpect(jsonPath("$.maskedToken").value("test••••••••oken"))
			.andExpect(jsonPath("$.accessToken").doesNotExist());
	}

	@Test
	@DisplayName("출금 시 계약을 함께 저장하고 금결원 요청에는 보금 계약 ID를 보내지 않는다")
	void savesWithdrawalWithContract() throws Exception {
		User tenant = user(Role.USER);
		Contract contract = contract(user(Role.USER), tenant);
		OpenBanking account = account(tenant);
		bank.expect(requestTo("http://localhost:8090/v2.0/transfer/withdraw/fin_num"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("contractId"))))
			.andRespond(withSuccess("""
				{"api_tran_id":"api-1","rsp_code":"A0000","rsp_message":"성공",
				 "bank_tran_id":"F123456789U000000001","bank_tran_date":"20261111",
				 "bank_rsp_code":"000","bank_rsp_message":"정상","tran_amt":"650000"}
				""", MediaType.APPLICATION_JSON));
		mockMvc.perform(csrfPost("/openbanking/accounts/" + account.getOpenBankingId() + "/withdrawals", user(Role.ADMIN))
			.header("X-Openbanking-Client-Id", "test").header("X-Openbanking-Client-Secret", "test")
			.header("X-Openbanking-Scope", "transfer")
			.content(withdrawalJson(contract.getContractId(), account.getFintechNum())))
			.andExpect(status().isOk());
		var saved = transfers.findAll().get(0);
		assertEquals(contract.getContractId(), saved.getContract().getContractId());
		assertEquals(TransferType.WITHDRAW, saved.getTransfer_type());
		assertEquals(TransferStatus.SUCCESS, saved.getStatus());
	}

	@ParameterizedTest
	@CsvSource({"000,SUCCESS,20261111", "101,FAILED,20261111", "'',PENDING_CONFIRMATION,''"})
	@DisplayName("기관 토큰을 사용한 송금에 계약·성공·실패·미확정 상태를 저장한다")
	void savesRemittanceWithContract(String bankCode, TransferStatus expectedStatus, String bankDate) throws Exception {
		User landlord = user(Role.USER);
		Contract contract = contract(landlord, user(Role.USER));
		OpenBanking account = account(landlord);
		bank.expect(requestTo("http://localhost:8090/v2.0/transfer/deposit/fin_num"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("""
				{"api_tran_id":"api-2","rsp_code":"A0000","rsp_message":"접수",
				 "res_list":[{"bank_tran_id":"F123456789U000000002","bank_tran_date":"%s",
				 "bank_rsp_code":"%s","bank_rsp_message":"처리 결과"}]}
				""".formatted(bankDate, bankCode), MediaType.APPLICATION_JSON));
		mockMvc.perform(remittancePost(user(Role.ADMIN)).content(remittanceJson(contract.getContractId(), account.getFintechNum())))
			.andExpect(status().isOk());
		entityManager.flush();
		var saved = transfers.findAll().get(0);
		assertEquals(contract.getContractId(), saved.getContract().getContractId());
		assertEquals(TransferType.REMITTANCE, saved.getTransfer_type());
		assertEquals(expectedStatus, saved.getStatus());
	}

	@Test
	@DisplayName("계약의 임차인과 출금 계좌 소유자가 다르면 금결원 호출 전에 거절한다")
	void rejectsUnrelatedWithdrawal() throws Exception {
		User tenant = user(Role.USER);
		User other = user(Role.USER);
		User admin = user(Role.ADMIN);
		Contract contract = contract(other, tenant);
		OpenBanking otherAccount = account(other);
		mockMvc.perform(csrfPost("/openbanking/accounts/" + otherAccount.getOpenBankingId() + "/withdrawals", admin)
			.header("X-Openbanking-Client-Id", "test").header("X-Openbanking-Client-Secret", "test")
			.header("X-Openbanking-Scope", "transfer")
			.content(withdrawalJson(contract.getContractId(), otherAccount.getFintechNum())))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		OpenBanking ownAccount = account(tenant);
		Contract otherContract = contract(tenant, other);
		mockMvc.perform(csrfPost("/openbanking/accounts/" + ownAccount.getOpenBankingId() + "/withdrawals", admin)
			.header("X-Openbanking-Client-Id", "test").header("X-Openbanking-Client-Secret", "test")
			.header("X-Openbanking-Scope", "transfer")
			.content(withdrawalJson(otherContract.getContractId(), ownAccount.getFintechNum())))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		assertEquals(0, transfers.count());
	}

	@Test
	@DisplayName("계약의 임대인과 다른 계좌로는 송금할 수 없다")
	void rejectsIncorrectRemittance() throws Exception {
		User landlord = user(Role.USER);
		Contract contract = contract(landlord, user(Role.USER));
		account(landlord);
		mockMvc.perform(remittancePost(user(Role.ADMIN)).content(remittanceJson(contract.getContractId(), "other-fintech")))
			.andExpect(status().isBadRequest());
		assertFalse(transfers.findAll().iterator().hasNext());
	}

	@Test
	@DisplayName("계약 ID 누락·없는 계약·월세가 아닌 계약은 저장하지 않는다")
	void requiresExistingMonthlyContract() throws Exception {
		User admin = user(Role.ADMIN);
		mockMvc.perform(remittancePost(admin).content(remittanceJson(null, "fintech")))
			.andExpect(status().isBadRequest());
		mockMvc.perform(remittancePost(admin).content(remittanceJson(Long.MAX_VALUE, "fintech")))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"));
		Contract jeonse = Contract.builder().property(property(admin)).landlord(admin).tenant(admin).agent(admin)
			.leaseType(LeaseType.JEONSE).deposit(BigDecimal.TEN)
			.startAt(LocalDateTime.now()).endAt(LocalDateTime.now().plusYears(2)).build();
		entityManager.persist(jeonse);
		mockMvc.perform(remittancePost(admin).content(remittanceJson(jeonse.getContractId(), "fintech")))
			.andExpect(status().isBadRequest());
		assertEquals(0, transfers.count());
	}

	private User user(Role role) {
		return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
			.name("이체 사용자").phone("01012345678").role(role).build());
	}

	private Property property(User landlord) {
		var property = Property.builder().landlord(landlord).leaseType(LeaseType.MONTHLY)
			.deposit(BigDecimal.valueOf(10000000)).monthlyRent(BigDecimal.valueOf(650000))
			.roadAddress("서울 강남구 테스트로 1").detailAddress("101호").build();
		entityManager.persist(property);
		return property;
	}

	private Contract contract(User landlord, User tenant) {
		var contract = Contract.builder().property(property(landlord)).landlord(landlord).tenant(tenant).agent(landlord)
			.leaseType(LeaseType.MONTHLY).deposit(BigDecimal.valueOf(10000000)).monthlyRent(BigDecimal.valueOf(650000))
			.startAt(LocalDateTime.of(2026, 1, 1, 0, 0)).endAt(LocalDateTime.of(2027, 12, 31, 0, 0)).build();
		entityManager.persist(contract);
		return contract;
	}

	private OpenBanking account(User user) {
		return accounts.saveAndFlush(OpenBanking.builder().user(user).fintechNum("fintech-" + user.getUserId())
			.accessToken("mock-access-token").refreshToken("mock-refresh-token").userSeqNo("mock-seq")
			.tokenExpiresAt(LocalDateTime.now().plusDays(1)).build());
	}

	private OpenBankingTransfer transfer(Contract contract, TransferType type, String requestedAt) {
		return transfers.saveAndFlush(OpenBankingTransfer.builder().contract(contract).transfer_type(type)
			.bankTranId(UUID.randomUUID().toString().replace("-", "").substring(0, 20))
			.requestAmount(BigDecimal.valueOf(650000)).requestedAt(LocalDateTime.parse(requestedAt))
			.status(TransferStatus.SUCCESS).bankTranDate(LocalDate.parse(requestedAt.substring(0, 10)))
			.apiTranId("api-test").apiResponseCode("A0000").apiResponseMessage("성공")
			.bankResponseCode("000").bankResponseMessage("정상").build());
	}

	private String bearer(User user) {
		return "Bearer " + jwtUtil.createAccess(user.getUserId(), UUID.randomUUID().toString());
	}

	private MockHttpServletRequestBuilder csrfPost(String path, User user) throws Exception {
		var response = mockMvc.perform(get("/auth/csrf")).andReturn().getResponse();
		var csrf = new JsonMapper().readTree(response.getContentAsString());
		return post(path).header("Authorization", bearer(user)).cookie(response.getCookie("BOGEUM_CSRF"))
			.header(csrf.get("headerName").asText(), csrf.get("token").asText()).contentType(MediaType.APPLICATION_JSON);
	}

	private MockHttpServletRequestBuilder remittancePost(User user) throws Exception {
		return csrfPost("/openbanking/remittances", user).header("X-Openbanking-Institution-Token", "mock-institution-token")
			.param("reqClientFintechUseNum", "sender-fintech");
	}

	private String withdrawalJson(Long contractId, String fintechNum) {
		return """
			{"contractId":%s,"bankTranId":"F123456789U000000001","fintechUseNum":"%s",
			 "tranAmt":"650000","tranDtime":"20261111090000","reqClientName":"임차인",
			 "reqClientNum":"tenant","transferPurpose":"TR"}
			""".formatted(contractId, fintechNum);
	}

	private String remittanceJson(Long contractId, String fintechNum) {
		return """
			{"contractId":%s,"reqCnt":"1","reqList":[{"bankTranId":"F123456789U000000002",
			 "fintechUseNum":"%s","tranAmt":"650000"}]}
			""".formatted(contractId, fintechNum);
	}
}
