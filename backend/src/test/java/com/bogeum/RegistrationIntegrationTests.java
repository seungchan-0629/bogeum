package com.bogeum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bogeum.domain.AgentVerificationStatus;
import com.bogeum.domain.Contract;
import com.bogeum.domain.HousingRole;
import com.bogeum.domain.IdentityVerification;
import com.bogeum.domain.Member;
import com.bogeum.domain.MemberType;
import com.bogeum.exception.ApiException;
import com.bogeum.repository.AgentProfileRepository;
import com.bogeum.repository.ConsentRecordRepository;
import com.bogeum.repository.ContractRepository;
import com.bogeum.repository.IdentityVerificationRepository;
import com.bogeum.repository.MemberProfileRepository;
import com.bogeum.repository.MemberRepository;
import com.bogeum.service.IdentityVerificationService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationIntegrationTests {

	private static final String PASSWORD = "Signup-password-2026!";

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private MemberRepository members;
	@Autowired private MemberProfileRepository memberProfiles;
	@Autowired private AgentProfileRepository agentProfiles;
	@Autowired private ConsentRecordRepository consentRecords;
	@Autowired private ContractRepository contracts;
	@Autowired private IdentityVerificationRepository verifications;
	@Autowired private IdentityVerificationService verificationService;
	@Autowired private TransactionTemplate transactions;
	@Autowired private PasswordEncoder passwordEncoder;

	@BeforeEach
	@AfterEach
	void cleanDatabase() {
		contracts.deleteAll();
		consentRecords.deleteAll();
		memberProfiles.deleteAll();
		agentProfiles.deleteAll();
		members.deleteAll();
		verifications.deleteAll();
	}

	@Test
	void generalSignupPersistsProfileConsentsAndHashedPasswordThenUsesExistingLogin() throws Exception {
		String token = issueToken("  NEW@EXAMPLE.COM  ");
		Map<String, Object> body = memberRequest("  NEW@EXAMPLE.COM  ", token);
		body.put("memberType", "ADMIN");
		MvcResult result = signup("member", body, 201);
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		assertThat(response.get("email").asText()).isEqualTo("new@example.com");
		assertThat(response.get("memberType").asText()).isEqualTo("GENERAL");
		assertThat(response.has("housingRoles")).isFalse();
		assertThat(response.has("password")).isFalse();
		assertThat(response.has("passwordHash")).isFalse();
		var member = members.findByEmail("new@example.com").orElseThrow();
		assertThat(member.getMemberType()).isEqualTo(MemberType.GENERAL);
		assertThat(member.getPasswordHash()).isNotEqualTo(PASSWORD);
		assertThat(passwordEncoder.matches(PASSWORD, member.getPasswordHash())).isTrue();
		transactions.executeWithoutResult(ignored -> {
			var profile = memberProfiles.findById(member.getId()).orElseThrow();
			assertThat(profile.getName()).isEqualTo("테스트 회원");
			assertThat(profile.getPhoneNumber()).isEqualTo("010-1234-5678");
		});
		assertThat(consentRecords.findByMemberId(member.getId())).hasSize(2)
			.allSatisfy(consent -> {
				assertThat(consent.getTermsVersion()).isEqualTo("general-dev-v1");
				assertThat(consent.getAgreedAt()).isNotNull();
			});
		assertThat(verifications.findAll().get(0).getTokenHash()).hasSize(64).isNotEqualTo(token);
		assertThat(verifications.findAll().get(0).getConsumedAt()).isNotNull();
		mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
		MockHttpSession session = login("new@example.com");
		mockMvc.perform(get("/api/auth/me").session(session))
			.andExpect(status().isOk()).andExpect(jsonPath("$.email").value("new@example.com"));
	}

	@Test
	void housingRoleIsDeterminedByEachPersistedContract() {
		Member first = members.saveAndFlush(new Member("first@example.com", "hash", MemberType.GENERAL));
		Member second = members.saveAndFlush(new Member("second@example.com", "hash", MemberType.GENERAL));
		Member third = members.saveAndFlush(new Member("third@example.com", "hash", MemberType.AGENT));
		Long firstContractId = contracts.saveAndFlush(new Contract(first, second)).getId();
		Long secondContractId = contracts.saveAndFlush(new Contract(third, first)).getId();

		Contract firstContract = contracts.findById(firstContractId).orElseThrow();
		Contract secondContract = contracts.findById(secondContractId).orElseThrow();
		assertThat(firstContract.roleOf(first.getId())).contains(HousingRole.LANDLORD);
		assertThat(secondContract.roleOf(first.getId())).contains(HousingRole.TENANT);
		assertThat(secondContract.roleOf(third.getId())).contains(HousingRole.LANDLORD);
		assertThat(firstContract.roleOf(third.getId())).isEmpty();
		assertThat(secondContract.roleOf(second.getId())).isEmpty();
		assertThat(firstContract.roleOf(null)).isEmpty();
		assertThatThrownBy(() -> new Contract(first, first)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void agentSignupReusesAccountCreationAndStartsWithDocumentsRequired() throws Exception {
		Map<String, Object> body = agentRequest("agent-new@example.com");
		body.put("memberType", "ADMIN");
		body.put("verificationStatus", "APPROVED");
		Instant before = Instant.now().plus(3, ChronoUnit.DAYS);
		MvcResult result = signup("agent", body, 201);
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		assertThat(response.get("memberType").asText()).isEqualTo("AGENT");
		assertThat(response.get("agentVerificationStatus").asText()).isEqualTo("DOCUMENT_REQUIRED");
		var member = members.findByEmail("agent-new@example.com").orElseThrow();
		assertThat(passwordEncoder.matches(PASSWORD, member.getPasswordHash())).isTrue();
		var profile = agentProfiles.findById(member.getId()).orElseThrow();
		assertThat(profile.getOfficeName()).isEqualTo("보금 공인중개사무소");
		assertThat(profile.getOfficeAddress()).isEqualTo("서울시 테스트로 1");
		assertThat(profile.getVerificationStatus()).isEqualTo(AgentVerificationStatus.DOCUMENT_REQUIRED);
		assertThat(profile.getDocumentSubmissionDeadline()).isBetween(before, Instant.now().plus(3, ChronoUnit.DAYS));
		assertThat(consentRecords.findByMemberId(member.getId())).hasSize(2)
			.allSatisfy(consent -> assertThat(consent.getTermsVersion()).isEqualTo("agent-dev-v1"));
		assertThat(verifications.count()).isZero();
		MockHttpSession session = login("agent-new@example.com");
		mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
			.andExpect(jsonPath("$.agentVerificationStatus").value("DOCUMENT_REQUIRED"));
		mockMvc.perform(get("/api/admin/agents").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void duplicateEmailIsRejectedAcrossBothMemberTypesAndCaseVariants() throws Exception {
		signup("agent", agentRequest("same@example.com"), 201);
		signup("agent", agentRequest("  SAME@EXAMPLE.COM "), 409);
		String token = issueToken("same@example.com");
		MvcResult duplicate = signup("member", memberRequest("same@example.com", token), 409);
		assertCode(duplicate, "EMAIL_ALREADY_EXISTS");
		assertThat(members.count()).isEqualTo(1);
		assertThat(agentProfiles.count()).isEqualTo(1);
		assertThat(memberProfiles.count()).isZero();
		assertThat(consentRecords.count()).isEqualTo(2);
	}

	@ParameterizedTest
	@ValueSource(strings = {"wrong-confirmation", "short", "unicode-too-long"})
	void commonPasswordValidationRejectsInvalidInputForBothSignupTypes(String scenario) throws Exception {
		for (String type : List.of("member", "agent")) {
			String email = type + "@example.com";
			Map<String, Object> body = type.equals("member")
				? memberRequest(email, issueToken(email)) : agentRequest(email);
			String password = scenario.equals("short") ? "short"
				: scenario.equals("unicode-too-long") ? "가".repeat(25) : PASSWORD;
			body.put("account", Map.of("email", email, "password", password,
				"passwordConfirm", scenario.equals("wrong-confirmation") ? "different-password" : password));
			assertCode(signup(type, body, 400), "VALIDATION_FAILED");
		}
		assertNoSignupData();
		assertThat(verifications.findAll()).allSatisfy(proof -> assertThat(proof.getConsumedAt()).isNull());
	}

	@ParameterizedTest
	@ValueSource(strings = {"refused", "duplicate", "stale-version", "other-member-type-version"})
	void failedConsentRollsBackAccountProfileAndProofConsumption(String scenario) throws Exception {
		String email = "rollback@example.com";
		String token = issueToken(email);
		for (String type : List.of("member", "agent")) {
			Map<String, Object> body = type.equals("member") ? memberRequest(email, token) : agentRequest(email);
			String version = type.equals("member") ? "general-dev-v1" : "agent-dev-v1";
			List<Map<String, Object>> agreements = agreements(version);
			if (scenario.equals("refused")) agreements.get(1).put("agreed", false);
			if (scenario.equals("duplicate")) agreements.get(1).put("type", "PRIVACY_COLLECTION");
			if (scenario.equals("stale-version")) agreements.get(1).put("version", "old-v0");
			if (scenario.equals("other-member-type-version")) agreements.get(1).put("version",
				type.equals("member") ? "agent-dev-v1" : "general-dev-v1");
			body.put("consents", agreements);
			boolean stale = scenario.contains("version");
			assertCode(signup(type, body, stale ? 409 : 400), stale ? "CONSENT_VERSION_MISMATCH" : "CONSENT_REQUIRED");
			assertNoSignupData();
			assertThat(verifications.findAll().get(0).getConsumedAt()).isNull();
		}
		// 실패한 요청에서 증빙 소비도 취소되므로 같은 증빙으로 올바르게 재시도할 수 있다.
		signup("member", memberRequest(email, token), 201);
	}

	@Test
	void rejectsMissingAndNestedInvalidFieldsWithoutPartialWrites() throws Exception {
		MvcResult missing = signup("member", Map.of(), 400);
		assertThat(objectMapper.readTree(missing.getResponse().getContentAsString())
			.get("fieldErrors").has("account")).isTrue();
		Map<String, Object> agent = agentRequest("invalid@example.com");
		agent.put("office", Map.of("name", "", "address", "", "phoneNumber", "invalid"));
		MvcResult result = signup("agent", agent, 400);
		JsonNode errors = objectMapper.readTree(result.getResponse().getContentAsString()).get("fieldErrors");
		assertThat(errors.has("office.name")).isTrue();
		assertThat(errors.has("office.phoneNumber")).isTrue();
		agent.remove("office");
		signup("agent", agent, 400);
		assertNoSignupData();
	}

	@Test
	void rejectsMissingUnknownExpiredAndWrongEmailIdentityProofs() throws Exception {
		for (String token : List.of("not-issued", issueToken("someone-else@example.com"))) {
			assertCode(signup("member", memberRequest("new@example.com", token), 400), "INVALID_IDENTITY_VERIFICATION");
		}
		String expired = "expired-verification-token";
		verifications.save(new IdentityVerification(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
			.digest(expired.getBytes(StandardCharsets.UTF_8))), "new@example.com", "테스트", "010-1234-5678",
			Instant.now().minusSeconds(1)));
		assertCode(signup("member", memberRequest("new@example.com", expired), 400), "INVALID_IDENTITY_VERIFICATION");
		Map<String, Object> missing = memberRequest("new@example.com", "");
		signup("member", missing, 400);
		assertNoSignupData();
	}

	@Test
	void consumedIdentityProofCannotBeReused() throws Exception {
		String token = issueToken("once@example.com");
		signup("member", memberRequest("once@example.com", token), 201);
		assertThatThrownBy(() -> transactions.executeWithoutResult(ignored ->
			verificationService.consume(token, "once@example.com"))).isInstanceOf(ApiException.class)
			.hasMessage("유효한 본인 인증 증빙이 필요합니다.");
	}

	@Test
	void concurrentSignupWithSameNormalizedEmailOnlyCreatesOneCompleteAccount() throws Exception {
		var executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			var first = executor.submit(() -> {
				start.await(10, TimeUnit.SECONDS);
				return signupWithoutExpectation("agent", agentRequest("concurrent@example.com"))
					.getResponse().getStatus();
			});
			var second = executor.submit(() -> {
				start.await(10, TimeUnit.SECONDS);
				return signupWithoutExpectation("agent", agentRequest(" CONCURRENT@EXAMPLE.COM "))
					.getResponse().getStatus();
			});
			start.countDown();
			assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(201, 409);
		} finally {
			executor.shutdownNow();
		}
		assertThat(members.count()).isEqualTo(1);
		assertThat(agentProfiles.count()).isEqualTo(1);
		assertThat(consentRecords.count()).isEqualTo(2);
	}

	@Test
	void termsArePublicVersionedAndMemberTypeSpecific() throws Exception {
		mockMvc.perform(get("/api/terms").param("memberType", "GENERAL"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.registrationAvailable").value(true))
			.andExpect(jsonPath("$.draft").value(true))
			.andExpect(jsonPath("$.terms[0].version").value("general-dev-v1"));
		mockMvc.perform(get("/api/terms").param("memberType", "AGENT"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.terms[0].version").value("agent-dev-v1"));
		mockMvc.perform(get("/api/terms").param("memberType", "ADMIN")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/terms").param("memberType", "INVALID")).andExpect(status().isBadRequest());
	}

	@Test
	void signupAndDevelopmentVerificationRequireCsrf() throws Exception {
		for (String path : List.of("/api/auth/signup/member", "/api/auth/signup/agent", "/api/dev/identity-verifications")) {
			mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		}
		assertNoSignupData();
	}

	private String issueToken(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/dev/identity-verifications").with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
				"email", email, "name", "테스트 회원", "phoneNumber", "010-1234-5678"))))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.developmentOnly").value(true)).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
	}

	private Map<String, Object> memberRequest(String email, String token) {
		Map<String, Object> result = new HashMap<>();
		result.put("account", account(email));
		result.put("identityVerificationToken", token);
		result.put("consents", agreements("general-dev-v1"));
		return result;
	}

	private Map<String, Object> agentRequest(String email) {
		Map<String, Object> result = new HashMap<>();
		result.put("account", account(email));
		result.put("phoneNumber", "010-1234-5678");
		result.put("office", Map.of("name", " 보금 공인중개사무소 ", "address", " 서울시 테스트로 1 ",
			"phoneNumber", "02-1234-5678", "registrationNumber", "TEST-123"));
		result.put("consents", agreements("agent-dev-v1"));
		return result;
	}

	private Map<String, String> account(String email) {
		return Map.of("email", email, "password", PASSWORD, "passwordConfirm", PASSWORD);
	}

	private List<Map<String, Object>> agreements(String version) {
		return new ArrayList<>(List.of(
			new HashMap<>(Map.of("type", "PRIVACY_COLLECTION", "version", version, "agreed", true)),
			new HashMap<>(Map.of("type", "THIRD_PARTY_PROVISION", "version", version, "agreed", true))));
	}

	private MvcResult signup(String type, Map<String, ?> request, int expectedStatus) throws Exception {
		MvcResult result = signupWithoutExpectation(type, request);
		assertThat(result.getResponse().getStatus()).withFailMessage("Response: %s",
			result.getResponse().getContentAsString()).isEqualTo(expectedStatus);
		return result;
	}

	private MvcResult signupWithoutExpectation(String type, Map<String, ?> request) throws Exception {
		return mockMvc.perform(post("/api/auth/signup/" + type).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andReturn();
	}

	private MockHttpSession login(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
				Map.of("email", email, "password", PASSWORD))))
			.andExpect(status().isOk()).andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private void assertCode(MvcResult result, String code) throws Exception {
		assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText()).isEqualTo(code);
	}

	private void assertNoSignupData() {
		assertThat(members.count()).isZero();
		assertThat(memberProfiles.count()).isZero();
		assertThat(agentProfiles.count()).isZero();
		assertThat(consentRecords.count()).isZero();
	}
}
