package com.bogeum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.RiskReason;
import com.bogeum.common.enums.RiskStatus;
import com.bogeum.common.enums.RiskTargetType;
import com.bogeum.common.enums.Role;
import com.bogeum.common.enums.UserStatus;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.risk.RiskRepository;
import com.bogeum.domain.risk.model.RiskDto;
import com.bogeum.domain.risk.model.RiskInfo;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.JwtUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

/** 실제 JWT·CSRF 필터와 H2로 #42 신규 등록 정책을 검증한다. #44 조회 정책은 바꾸지 않는다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-admin-risk-user-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
	"spring.jpa.open-in-view=false", "auth.cookie-secure=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AdminRiskUserCreateTests {
	private static final String PATH = "/admin/risk-users";
	@Autowired private MockMvc mvc;
	@Autowired private RiskRepository risks;
	@Autowired private UserRepository users;
	@Autowired private PropertyRepository properties;
	@Autowired private JwtUtil jwt;
	@Autowired private JsonMapper mapper;
	@Autowired private EntityManager entityManager;

	@Test
	void savesAuthenticatedAdministratorAndReturnsItemVisibleInFilteredList() throws Exception {
		var target = user(Role.USER);
		var administrator = user(Role.ADMIN);
		var otherAdministrator = user(Role.ADMIN);
		String body = mapper.writeValueAsString(Map.of("userId", target.getUserId(),
			"reasonType", "RENT_UNPAID", "detail", "  11월분 미납\n확인  ",
			"registeredBy", otherAdministrator.getUserId(), "riskStatus", "RELEASED", "targetType", "PROPERTY",
			"targetProperty", 123, "evidenceFileKey", "arbitrary/private.pdf", "userType", "AGENT"));
		var result = mvc.perform(csrfPost().header("Authorization", token(administrator))
			.content(body)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.userId").value(target.getUserId()))
			.andExpect(jsonPath("$.userType").value("TENANT"))
			.andExpect(jsonPath("$.reasonType").value("RENT_UNPAID"))
			.andExpect(jsonPath("$.detail").value("  11월분 미납\n확인  "))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andExpect(jsonPath("$.hasEvidence").value(false))
			.andExpect(jsonPath("$.*", org.hamcrest.Matchers.hasSize(8))).andReturn();
		long riskInfoId = mapper.readTree(result.getResponse().getContentAsString()).get("riskInfoId").asLong();
		entityManager.flush();
		entityManager.clear();
		var saved = risks.findById(riskInfoId).orElseThrow();
		assertThat(saved.getTargetUser().getUserId()).isEqualTo(target.getUserId());
		assertThat(saved.getTargetType()).isEqualTo(RiskTargetType.USER);
		assertThat(saved.getTargetProperty()).isNull();
		assertThat(saved.getReasonType()).isEqualTo(RiskReason.RENT_UNPAID);
		assertThat(saved.getDetail()).isEqualTo("  11월분 미납\n확인  ");
		assertThat(saved.getRiskStatus()).isEqualTo(RiskStatus.REGISTERED);
		assertThat(saved.getRegisteredBy().getUserId()).isEqualTo(administrator.getUserId());
		assertThat(saved.getEvidenceFileKey()).isNull();
		assertThat(saved.getUpdateReason()).isNull();
		assertThat(saved.getReleasedAt()).isNull();
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(
			"email", "phone", "passwordHash", "address", "registeredBy", "evidenceFileKey", "arbitrary/private.pdf");
		mvc.perform(get(PATH).header("Authorization", token(administrator))
			.param("type", "TENANT").param("keyword", "등록 대상").param("page", "1"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.riskUsers[0].riskInfoId").value(riskInfoId))
			.andExpect(jsonPath("$.riskUsers[0].detail").value("  11월분 미납\n확인  "));
	}

	@ParameterizedTest
	@MethodSource("roleAndReasonCombinations")
	void validatesEveryTargetRoleAndReasonUsingStoredRole(Role role, RiskReason reason, boolean allowed) throws Exception {
		var target = user(role);
		// 요청의 역할·분류를 위조해도 DB에 저장한 실제 역할로 판단해야 한다.
		String body = mapper.writeValueAsString(Map.of("userId", target.getUserId(), "reasonType", reason,
			"role", "AGENT", "userType", "AGENT"));
		var result = mvc.perform(csrfPost().header("Authorization", token(user(Role.ADMIN))).content(body))
			.andExpect(status().is(allowed ? 201 : 400));
		if (!allowed) {
			result.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
		assertThat(risks.count()).isEqualTo(allowed ? 1 : 0);
	}

	static Stream<Arguments> roleAndReasonCombinations() {
		Map<Role, Set<RiskReason>> allowed = Map.of(
			Role.USER, Set.of(RiskReason.RENT_UNPAID, RiskReason.RENT_OVERDUE,
				RiskReason.DEPOSIT_UNPAID, RiskReason.FALSE_LISTING),
			Role.AGENT, Set.of(RiskReason.RENT_UNPAID, RiskReason.RENT_OVERDUE,
				RiskReason.DEPOSIT_UNPAID, RiskReason.FALSE_LISTING,
				RiskReason.NO_PROPERTY_IMAGE, RiskReason.AGENT_CHANGE_HISTORY),
			Role.LANDLORD, Set.of(RiskReason.DEPOSIT_UNPAID, RiskReason.FALSE_LISTING),
			Role.TENANT, Set.of(RiskReason.RENT_UNPAID, RiskReason.RENT_OVERDUE));
		return Arrays.stream(Role.values()).flatMap(role -> Arrays.stream(RiskReason.values())
			.map(reason -> Arguments.of(role, reason, allowed.getOrDefault(role, Set.of()).contains(reason))));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", "\t\n", "  첫 줄\n둘째 줄  "})
	void preservesNullableAndBlankDetail(String detail) throws Exception {
		var target = user(Role.USER);
		String body = mapper.writeValueAsString(new RiskDto.UserRegReq(target.getUserId(), RiskReason.FALSE_LISTING, detail));
		mvc.perform(csrfPost().header("Authorization", token(user(Role.ADMIN))).content(body))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.is(detail)));
		entityManager.flush();
		entityManager.clear();
		assertThat(risks.findAll().get(0).getDetail()).isEqualTo(detail);
	}

	@Test
	void acceptsOmittedDetail() throws Exception {
		var target = user(Role.USER);
		mvc.perform(csrfPost().header("Authorization", token(user(Role.ADMIN)))
			.content(mapper.writeValueAsString(Map.of("userId", target.getUserId(), "reasonType", "RENT_OVERDUE"))))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.nullValue()));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{}", "{\"userId\":null,\"reasonType\":\"RENT_UNPAID\"}",
		"{\"userId\":0,\"reasonType\":\"RENT_UNPAID\"}", "{\"userId\":-1,\"reasonType\":\"RENT_UNPAID\"}",
		"{\"userId\":\"abc\",\"reasonType\":\"RENT_UNPAID\"}", "{\"userId\":9223372036854775808,\"reasonType\":\"RENT_UNPAID\"}",
		"{\"reasonType\":\"RENT_UNPAID\"}", "{"
	})
	void rejectsInvalidMemberInputAndMalformedJson(String body) throws Exception {
		mvc.perform(csrfPost().header("Authorization", token(user(Role.ADMIN))).content(body))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		assertThat(risks.count()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", ",\"reasonType\":null", ",\"reasonType\":\"\"",
		",\"reasonType\":\"UNKNOWN\"", ",\"reasonType\":\"rent_unpaid\"", ",\"reasonType\":999"})
	void rejectsMissingAndInvalidReason(String reasonField) throws Exception {
		var target = user(Role.USER);
		mvc.perform(csrfPost().header("Authorization", token(user(Role.ADMIN)))
			.content("{\"userId\":" + target.getUserId() + reasonField + "}"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		assertThat(risks.count()).isZero();
	}

	@Test
	void rejectsMissingMember() throws Exception {
		mvc.perform(csrfPost().header("Authorization", token(user(Role.ADMIN)))
			.content(mapper.writeValueAsString(new RiskDto.UserRegReq(Long.MAX_VALUE, RiskReason.RENT_UNPAID, null))))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RISK_USER_NOT_FOUND"));
		assertThat(risks.count()).isZero();
	}

	@Test
	void rejectsRepeatedRegistrationEvenWithAnotherAdministratorOrDetail() throws Exception {
		var target = user(Role.USER);
		mvc.perform(csrfPost().header("Authorization", token(user(Role.ADMIN)))
			.content(body(target, RiskReason.RENT_UNPAID, "최초 기록"))).andExpect(status().isCreated());
		entityManager.flush();
		entityManager.clear();
		var original = risks.findAll().get(0);
		mvc.perform(csrfPost().header("Authorization", token(user(Role.ADMIN)))
			.content(body(target, RiskReason.RENT_UNPAID, "다른 사건")))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RISK_ALREADY_REGISTERED"));
		entityManager.clear();
		var unchanged = risks.findById(original.getRiskInfoId()).orElseThrow();
		assertThat(risks.count()).isEqualTo(1);
		assertThat(unchanged.getDetail()).isEqualTo("최초 기록");
		assertThat(unchanged.getRegisteredBy().getUserId()).isEqualTo(original.getRegisteredBy().getUserId());
		assertThat(unchanged.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
	}

	@Test
	void createsAlongsideCandidatesReleasedAndOtherRisksWithoutChangingThem() throws Exception {
		var target = user(Role.USER);
		var administrator = user(Role.ADMIN);
		var candidate = risk(target, RiskReason.RENT_UNPAID, RiskStatus.CANDIDATE, null);
		var released = risk(target, RiskReason.RENT_UNPAID, RiskStatus.RELEASED, administrator);
		var otherReason = risk(target, RiskReason.FALSE_LISTING, RiskStatus.REGISTERED, administrator);
		var otherMember = risk(user(Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED, administrator);
		var property = properties.saveAndFlush(Property.builder().landlord(target).leaseType(LeaseType.JEONSE)
			.deposit(BigDecimal.ONE).roadAddress("테스트 주소").build());
		var propertyRisk = risks.saveAndFlush(RiskInfo.builder().targetType(RiskTargetType.PROPERTY)
			.targetProperty(property).reasonType(RiskReason.MORTGAGE_RISK).riskStatus(RiskStatus.REGISTERED)
			.detail("매물 기록").evidenceFileKey("private/property.pdf").registeredBy(administrator).build());
		List<RiskInfo> originals = List.of(candidate, released, otherReason, otherMember, propertyRisk);
		// 저장 전 스냅샷과 다시 조회한 모든 기존 필드를 비교한다.
		var snapshots = originals.stream().map(this::snapshot).toList();
		mvc.perform(csrfPost().header("Authorization", token(administrator))
			.content(body(target, RiskReason.RENT_UNPAID, "신규 등록"))).andExpect(status().isCreated());
		entityManager.flush();
		entityManager.clear();
		assertThat(risks.count()).isEqualTo(6);
		for (int i = 0; i < originals.size(); i++) {
			assertThat(snapshot(risks.findById(originals.get(i).getRiskInfoId()).orElseThrow())).isEqualTo(snapshots.get(i));
		}
		mvc.perform(get(PATH).header("Authorization", token(administrator)).param("type", "TENANT"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
	}

	@ParameterizedTest
	@EnumSource(UserStatus.class)
	void doesNotChangeTargetAccountStatus(UserStatus status) throws Exception {
		var target = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
			.name("상태 보존").phone("01000000000").role(Role.USER).userStatus(status).build());
		var updatedAt = target.getUpdatedAt();
		mvc.perform(csrfPost().header("Authorization", token(user(Role.ADMIN)))
			.content(body(target, RiskReason.RENT_UNPAID, null))).andExpect(status().isCreated());
		entityManager.flush();
		entityManager.clear();
		var unchanged = users.findById(target.getUserId()).orElseThrow();
		assertThat(unchanged.getUserStatus()).isEqualTo(status);
		assertThat(unchanged.getUpdatedAt()).isEqualTo(updatedAt);
	}

	@ParameterizedTest
	@CsvSource({"a,65535,201", "a,65536,400", "가,21845,201", "가,21846,400"})
	void validatesTextColumnLimitInUtf8Bytes(String character, int repetitions, int status) throws Exception {
		var target = user(Role.USER);
		var result = mvc.perform(csrfPost().header("Authorization", token(user(Role.ADMIN)))
			.content(body(target, RiskReason.RENT_UNPAID, character.repeat(repetitions))))
			.andExpect(status().is(status));
		if (status == 400) {
			result.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
		assertThat(risks.count()).isEqualTo(status == 201 ? 1 : 0);
	}

	@ParameterizedTest
	@EnumSource(value = Role.class, names = {"USER", "AGENT", "LANDLORD", "TENANT"})
	void rejectsNonAdministratorRequesters(Role role) throws Exception {
		mvc.perform(csrfPost().header("Authorization", token(user(role)))
			.content(body(user(Role.USER), RiskReason.RENT_UNPAID, null)))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		assertThat(risks.count()).isZero();
	}

	@Test
	void rejectsUnauthenticatedAndInvalidJwtWithValidCsrf() throws Exception {
		String body = body(user(Role.USER), RiskReason.RENT_UNPAID, null);
		mvc.perform(csrfPost().content(body)).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		mvc.perform(csrfPost().header("Authorization", "Bearer invalid").content(body))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCESS_TOKEN_INVALID"));
		assertThat(risks.count()).isZero();
	}

	@Test
	void rejectsMissingMismatchedAndIncompleteCsrfEvenForAdministrator() throws Exception {
		String authorization = token(user(Role.ADMIN));
		String body = body(user(Role.USER), RiskReason.RENT_UNPAID, null);
		var csrfResponse = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
		var csrf = mapper.readTree(csrfResponse.getContentAsString());
		for (var request : List.of(
			post(PATH),
			post(PATH).cookie(csrfResponse.getCookie("BOGEUM_CSRF")),
			post(PATH).header(csrf.get("headerName").asText(), csrf.get("token").asText()),
			post(PATH).cookie(csrfResponse.getCookie("BOGEUM_CSRF")).header(csrf.get("headerName").asText(), "wrong"))) {
			mvc.perform(request.header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		}
		assertThat(risks.count()).isZero();
	}

	private MockHttpServletRequestBuilder csrfPost() throws Exception {
		var response = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
		var csrf = mapper.readTree(response.getContentAsString());
		return post(PATH).contentType(MediaType.APPLICATION_JSON).cookie(response.getCookie("BOGEUM_CSRF"))
			.header(csrf.get("headerName").asText(), csrf.get("token").asText());
	}

	private String body(User target, RiskReason reason, String detail) {
		return mapper.writeValueAsString(new RiskDto.UserRegReq(target.getUserId(), reason, detail));
	}

	private String token(User user) {
		return "Bearer " + jwt.createAccess(user.getUserId(), UUID.randomUUID().toString());
	}

	private User user(Role role) {
		return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
			.passwordHash("must-not-be-exposed").name("등록 대상").phone("01000000000")
			.address("비공개 주소").role(role).build());
	}

	private RiskInfo risk(User target, RiskReason reason, RiskStatus status, User administrator) {
		return risks.saveAndFlush(RiskInfo.builder().targetType(RiskTargetType.USER).targetUser(target)
			.reasonType(reason).riskStatus(status).registeredBy(administrator).detail("기존 기록")
			.evidenceFileKey("private/original.pdf").updateReason("기존 수정 사유")
			.releasedAt(status == RiskStatus.RELEASED ? LocalDateTime.of(2026, 11, 1, 12, 0) : null).build());
	}

	private List<Object> snapshot(RiskInfo risk) {
		return Arrays.asList(risk.getTargetType(), risk.getTargetUser() == null ? null : risk.getTargetUser().getUserId(),
			risk.getTargetProperty() == null ? null : risk.getTargetProperty().getPropertyId(), risk.getReasonType(),
			risk.getRiskStatus(), risk.getDetail(), risk.getEvidenceFileKey(),
			risk.getRegisteredBy() == null ? null : risk.getRegisteredBy().getUserId(), risk.getUpdateReason(),
			risk.getReleasedAt(), risk.getCreatedAt(), risk.getUpdatedAt());
	}
}
