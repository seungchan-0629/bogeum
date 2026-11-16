package com.bogeum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

import com.bogeum.common.enums.RiskReason;
import com.bogeum.common.enums.RiskStatus;
import com.bogeum.common.enums.RiskTargetType;
import com.bogeum.common.enums.Role;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.json.JsonMapper;

/** 실제 JWT·CSRF·H2와 서비스 트랜잭션의 커밋·롤백을 검증한다. 테스트 트랜잭션으로 감싸지 않는다 (#46). */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-admin-risk-user-update-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
	"spring.jpa.open-in-view=false", "auth.cookie-secure=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AdminRiskUserUpdateTests {
	private static final String PATH = "/admin/risk-users";
	@Autowired private MockMvc mvc;
	@Autowired private RiskRepository risks;
	@Autowired private UserRepository users;
	@Autowired private JwtUtil jwt;
	@Autowired private JsonMapper mapper;
	@Autowired private JdbcTemplate jdbc;

	@Test
	void updatesOnlyEditableFieldsAndKeepsLatestReasonAfterCommit() throws Exception {
		var target = user(Role.USER);
		var original = risk(target, RiskReason.RENT_UNPAID, RiskStatus.REGISTERED);
		long id = original.getRiskInfoId();
		var oldTime = LocalDateTime.of(2026, 1, 1, 12, 0);
		jdbc.update("update risk_info set created_at = ?, updated_at = ?, released_at = ? where risk_info_id = ?",
			oldTime, oldTime, oldTime, id);
		var before = row(id);
		var targetBefore = jdbc.queryForMap("select * from user where user_id = ?", target.getUserId());
		var administrator = user(Role.ADMIN);
		var forged = new TreeMap<String, Object>();
		forged.putAll(Map.of("reasonType", "RENT_OVERDUE", "detail", "  납부 내역\n정정  ",
			"updateReason", "  증빙 재확인  ", "userId", administrator.getUserId(), "registeredBy", administrator.getUserId(),
			"riskInfoId", Long.MAX_VALUE, "targetType", "PROPERTY", "riskStatus", "RELEASED",
			"evidenceFileKey", "forged.pdf", "targetProperty", 123));
		forged.putAll(Map.of("createdAt", "2030-01-01T00:00:00", "releasedAt", "2030-01-01T00:00:00",
			"updatedAt", "2030-01-01T00:00:00"));
		var result = mvc.perform(csrfPatch(id).header("Authorization", token(administrator))
			.content(mapper.writeValueAsString(forged)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.riskInfoId").value(id))
			.andExpect(jsonPath("$.userId").value(target.getUserId()))
			.andExpect(jsonPath("$.name").value(target.getName()))
			.andExpect(jsonPath("$.userType").value("TENANT"))
			.andExpect(jsonPath("$.reasonType").value("RENT_OVERDUE"))
			.andExpect(jsonPath("$.detail").value("  납부 내역\n정정  "))
			.andExpect(jsonPath("$.createdAt").value("2026-01-01T12:00:00"))
			.andExpect(jsonPath("$.hasEvidence").value(true))
			.andExpect(jsonPath("$.*", org.hamcrest.Matchers.hasSize(8))).andReturn();
		var after = row(id);
		assertThat(after.get("update_reason")).isEqualTo("  증빙 재확인  ");
		assertThat(after.get("updated_at")).isNotEqualTo(before.get("updated_at"));
		assertThat(immutableFields(after)).isEqualTo(immutableFields(before));
		assertThat(jdbc.queryForMap("select * from user where user_id = ?", target.getUserId())).isEqualTo(targetBefore);
		assertThat(result.getResponse().getContentAsString()).doesNotContain(
			"registeredBy", "evidenceFileKey", "updateReason", "updatedAt", "email", "phone", "passwordHash", "private/");
		mvc.perform(csrfPatch(id).header("Authorization", token(administrator))
			.content(body(RiskReason.RENT_OVERDUE, "두 번째 정정", "최신 수정 사유"))).andExpect(status().isOk());
		assertThat(row(id).get("update_reason")).isEqualTo("최신 수정 사유");
		assertThat(immutableFields(row(id))).isEqualTo(immutableFields(before));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\t\n", "  첫 줄\n둘째 줄  "})
	void preservesFinalDetailIncludingNullAndBlank(String detail) throws Exception {
		var original = risk(user(Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED);
		mvc.perform(csrfPatch(original.getRiskInfoId()).header("Authorization", token(user(Role.ADMIN)))
			.content(body(RiskReason.RENT_UNPAID, detail, "상세 정정")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.is(detail)));
		assertThat(row(original.getRiskInfoId()).get("detail")).isEqualTo(detail);
	}

	@Test
	void omittedDetailClearsExistingContent() throws Exception {
		var original = risk(user(Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED);
		mvc.perform(csrfPatch(original.getRiskInfoId()).header("Authorization", token(user(Role.ADMIN)))
			.content("{\"reasonType\":\"RENT_UNPAID\",\"updateReason\":\"상세 삭제\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.nullValue()));
		assertThat(row(original.getRiskInfoId()).get("detail")).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", ",\"updateReason\":null", ",\"updateReason\":\"\"",
		",\"updateReason\":\"   \"", ",\"updateReason\":\"\\t\\n\""})
	void rejectsMissingOrBlankUpdateReasonWithoutChanges(String reasonField) throws Exception {
		var original = risk(user(Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED);
		assertRejected(original, "{\"reasonType\":\"RENT_OVERDUE\",\"detail\":\"정정\"" + reasonField + "}",
			400, "INVALID_REQUEST");
	}

	@ParameterizedTest
	@CsvSource({"a,255,200", "a,256,400", "가,255,200", "가,256,400"})
	void validatesUpdateReasonLength(String character, int repetitions, int expectedStatus) throws Exception {
		var original = risk(user(Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED);
		String updateReason = character.repeat(repetitions);
		String request = body(RiskReason.RENT_OVERDUE, "정정", updateReason);
		if (expectedStatus == 400) {
			assertRejected(original, request, 400, "INVALID_REQUEST");
		} else {
			mvc.perform(csrfPatch(original.getRiskInfoId()).header("Authorization", token(user(Role.ADMIN)))
				.content(request)).andExpect(status().isOk());
			assertThat(row(original.getRiskInfoId()).get("update_reason")).isEqualTo(updateReason);
		}
	}

	@ParameterizedTest
	@CsvSource({"a,65535,200", "a,65536,400", "가,21845,200", "가,21846,400"})
	void validatesDetailUtf8ByteBoundary(String character, int repetitions, int expectedStatus) throws Exception {
		var original = risk(user(Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED);
		String detail = character.repeat(repetitions);
		String request = body(RiskReason.RENT_OVERDUE, detail, "내용 정정");
		if (expectedStatus == 400) {
			assertRejected(original, request, 400, "INVALID_REQUEST");
		} else {
			mvc.perform(csrfPatch(original.getRiskInfoId()).header("Authorization", token(user(Role.ADMIN)))
				.content(request)).andExpect(status().isOk());
			assertThat(row(original.getRiskInfoId()).get("detail")).isEqualTo(detail);
		}
	}

	@ParameterizedTest
	@EnumSource(RiskStatus.class)
	void acceptsOnlyRegisteredStatus(RiskStatus state) throws Exception {
		var original = risk(user(Role.USER), RiskReason.RENT_UNPAID, state);
		String request = body(RiskReason.RENT_OVERDUE, "정정", "증빙 확인");
		if (state == RiskStatus.REGISTERED) {
			mvc.perform(csrfPatch(original.getRiskInfoId()).header("Authorization", token(user(Role.ADMIN)))
				.content(request)).andExpect(status().isOk());
			assertThat(row(original.getRiskInfoId()).get("risk_status")).isEqualTo("REGISTERED");
		} else {
			assertRejected(original, request, 409, "RISK_NOT_EDITABLE");
		}
	}

	@ParameterizedTest
	@MethodSource("changedRoleAndReasonCombinations")
	void validatesChangedReasonAgainstStoredRole(Role role, RiskReason reason, boolean allowed) throws Exception {
		RiskReason oldReason = reason == RiskReason.RENT_UNPAID ? RiskReason.RENT_OVERDUE : RiskReason.RENT_UNPAID;
		var original = risk(user(role), oldReason, RiskStatus.REGISTERED);
		String request = mapper.writeValueAsString(Map.of("reasonType", reason, "detail", "정정",
			"updateReason", "역할 검증", "role", "AGENT", "userType", "AGENT"));
		if (allowed) {
			mvc.perform(csrfPatch(original.getRiskInfoId()).header("Authorization", token(user(Role.ADMIN)))
				.content(request)).andExpect(status().isOk());
			assertThat(row(original.getRiskInfoId()).get("reason_type")).isEqualTo(reason.name());
		} else {
			assertRejected(original, request, 400, "INVALID_REQUEST");
		}
	}

	static Stream<Arguments> changedRoleAndReasonCombinations() {
		Map<Role, Set<RiskReason>> allowed = Map.of(
			Role.USER, Set.of(RiskReason.RENT_UNPAID, RiskReason.RENT_OVERDUE, RiskReason.DEPOSIT_UNPAID, RiskReason.FALSE_LISTING),
			Role.AGENT, Set.of(RiskReason.RENT_UNPAID, RiskReason.RENT_OVERDUE, RiskReason.DEPOSIT_UNPAID,
				RiskReason.FALSE_LISTING, RiskReason.NO_PROPERTY_IMAGE, RiskReason.AGENT_CHANGE_HISTORY),
			Role.LANDLORD, Set.of(RiskReason.DEPOSIT_UNPAID, RiskReason.FALSE_LISTING),
			Role.TENANT, Set.of(RiskReason.RENT_UNPAID, RiskReason.RENT_OVERDUE));
		return Arrays.stream(Role.values()).flatMap(role -> Arrays.stream(RiskReason.values())
			.map(reason -> Arguments.of(role, reason, allowed.getOrDefault(role, Set.of()).contains(reason))));
	}

	@Test
	void rejectsUnchangedPropertyReasonWithoutChangingStoredData() throws Exception {
		var original = risk(user(Role.USER), RiskReason.MORTGAGE_RISK, RiskStatus.REGISTERED);
		assertRejected(original, body(RiskReason.MORTGAGE_RISK, "상세만 정정", "증빙 재확인"),
			400, "INVALID_REQUEST");
	}

	@Test
	void correctsStoredPropertyReasonToAllowedUserReason() throws Exception {
		var original = risk(user(Role.USER), RiskReason.MORTGAGE_RISK, RiskStatus.REGISTERED);
		var before = row(original.getRiskInfoId());
		mvc.perform(csrfPatch(original.getRiskInfoId()).header("Authorization", token(user(Role.ADMIN)))
			.content(body(RiskReason.RENT_OVERDUE, "사용자 위험정보로 정정", "사유 분류 정정")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.userType").value("TENANT"));
		var after = row(original.getRiskInfoId());
		assertThat(after.get("reason_type")).isEqualTo("RENT_OVERDUE");
		assertThat(after.get("detail")).isEqualTo("사용자 위험정보로 정정");
		assertThat(after.get("update_reason")).isEqualTo("사유 분류 정정");
		assertThat(immutableFields(after)).isEqualTo(immutableFields(before));
	}

	@Test
	void unchangedReasonSkipsCurrentRoleAndExistingDuplicateChecks() throws Exception {
		var target = user(Role.USER);
		var original = risk(target, RiskReason.NO_PROPERTY_IMAGE, RiskStatus.REGISTERED);
		var duplicate = risk(target, RiskReason.NO_PROPERTY_IMAGE, RiskStatus.REGISTERED);
		var duplicateBefore = row(duplicate.getRiskInfoId());
		mvc.perform(csrfPatch(original.getRiskInfoId()).header("Authorization", token(user(Role.ADMIN)))
			.content(body(RiskReason.NO_PROPERTY_IMAGE, "상세만 정정", "과거 정보 정정"))).andExpect(status().isOk());
		assertThat(row(original.getRiskInfoId()).get("detail")).isEqualTo("상세만 정정");
		assertThat(row(duplicate.getRiskInfoId())).isEqualTo(duplicateBefore);
	}

	@ParameterizedTest
	@EnumSource(RiskStatus.class)
	void changedReasonRejectsOnlyRegisteredDuplicate(RiskStatus duplicateStatus) throws Exception {
		var target = user(Role.USER);
		var original = risk(target, RiskReason.RENT_UNPAID, RiskStatus.REGISTERED);
		var duplicate = risk(target, RiskReason.RENT_OVERDUE, duplicateStatus);
		var otherUser = risk(user(Role.USER), RiskReason.RENT_OVERDUE, RiskStatus.REGISTERED);
		var duplicateBefore = row(duplicate.getRiskInfoId());
		var otherBefore = row(otherUser.getRiskInfoId());
		String request = body(RiskReason.RENT_OVERDUE, "정정", "사유 정정");
		if (duplicateStatus == RiskStatus.REGISTERED) {
			assertRejected(original, request, 409, "RISK_ALREADY_REGISTERED");
		} else {
			mvc.perform(csrfPatch(original.getRiskInfoId()).header("Authorization", token(user(Role.ADMIN)))
				.content(request)).andExpect(status().isOk());
		}
		assertThat(row(duplicate.getRiskInfoId())).isEqualTo(duplicateBefore);
		assertThat(row(otherUser.getRiskInfoId())).isEqualTo(otherBefore);
	}

	@Test
	void unchangedContentCannotReplaceUpdateReason() throws Exception {
		var original = risk(user(Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED);
		assertRejected(original, body(RiskReason.RENT_UNPAID, original.getDetail(), "사유만 교체"), 400, "INVALID_REQUEST");
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{", "{\"reasonType\":null,\"detail\":\"정정\",\"updateReason\":\"확인\"}",
		"{\"detail\":\"정정\",\"updateReason\":\"확인\"}",
		"{\"reasonType\":\"UNKNOWN\",\"updateReason\":\"확인\"}",
		"{\"reasonType\":\"rent_overdue\",\"updateReason\":\"확인\"}",
		"{\"reasonType\":999,\"updateReason\":\"확인\"}"})
	void rejectsMalformedOrMissingReason(String request) throws Exception {
		var original = risk(user(Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED);
		assertRejected(original, request, 400, "INVALID_REQUEST");
	}

	@Test
	void missingPropertyAndOrphanRisksReturnNotFound() throws Exception {
		String authorization = token(user(Role.ADMIN));
		String request = body(RiskReason.RENT_OVERDUE, "정정", "확인");
		mvc.perform(csrfPatch(Long.MAX_VALUE).header("Authorization", authorization).content(request))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RISK_INFO_NOT_FOUND"));
		var propertyRisk = risks.saveAndFlush(RiskInfo.builder().targetType(RiskTargetType.PROPERTY)
			.targetUser(user(Role.USER)).reasonType(RiskReason.MORTGAGE_RISK).riskStatus(RiskStatus.REGISTERED).build());
		assertRejected(propertyRisk, request, 404, "RISK_INFO_NOT_FOUND");
		var orphan = risks.saveAndFlush(RiskInfo.builder().targetType(RiskTargetType.USER)
			.reasonType(RiskReason.RENT_UNPAID).riskStatus(RiskStatus.REGISTERED).build());
		assertRejected(orphan, request, 404, "RISK_INFO_NOT_FOUND");
	}

	@Test
	void movingReasonChangesListTypeWithoutChangingCreationOrder() throws Exception {
		var target = user(Role.USER);
		var original = risk(target, RiskReason.RENT_UNPAID, RiskStatus.REGISTERED);
		var newer = risk(target, RiskReason.FALSE_LISTING, RiskStatus.REGISTERED);
		jdbc.update("update risk_info set created_at = ? where risk_info_id = ?",
			LocalDateTime.of(2026, 1, 1, 12, 0), original.getRiskInfoId());
		jdbc.update("update risk_info set created_at = ? where risk_info_id = ?",
			LocalDateTime.of(2026, 2, 1, 12, 0), newer.getRiskInfoId());
		String authorization = token(user(Role.ADMIN));
		mvc.perform(csrfPatch(original.getRiskInfoId()).header("Authorization", authorization)
			.content(body(RiskReason.DEPOSIT_UNPAID, "유형 이동", "증빙 확인"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.userType").value("LANDLORD"));
		mvc.perform(get(PATH).header("Authorization", authorization).param("type", "TENANT").param("keyword", target.getName()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get(PATH).header("Authorization", authorization).param("type", "LANDLORD").param("keyword", target.getName()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.riskUsers[0].riskInfoId").value(newer.getRiskInfoId()))
			.andExpect(jsonPath("$.riskUsers[1].riskInfoId").value(original.getRiskInfoId()));
	}

	@ParameterizedTest
	@EnumSource(value = Role.class, names = {"USER", "AGENT", "LANDLORD", "TENANT"})
	void deniesNonAdministratorsWithoutChanges(Role role) throws Exception {
		var original = risk(user(Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED);
		var before = row(original.getRiskInfoId());
		mvc.perform(csrfPatch(original.getRiskInfoId()).header("Authorization", token(user(role)))
			.content(body(RiskReason.RENT_OVERDUE, "정정", "확인")))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		assertThat(row(original.getRiskInfoId())).isEqualTo(before);
	}

	@Test
	void rejectsUnauthenticatedAndInvalidJwtWithValidCsrf() throws Exception {
		var original = risk(user(Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED);
		var before = row(original.getRiskInfoId());
		String request = body(RiskReason.RENT_OVERDUE, "정정", "확인");
		mvc.perform(csrfPatch(original.getRiskInfoId()).content(request))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		mvc.perform(csrfPatch(original.getRiskInfoId()).header("Authorization", "Bearer invalid").content(request))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCESS_TOKEN_INVALID"));
		assertThat(row(original.getRiskInfoId())).isEqualTo(before);
	}

	@Test
	void rejectsMissingIncompleteAndMismatchedCsrf() throws Exception {
		var original = risk(user(Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED);
		var before = row(original.getRiskInfoId());
		String path = PATH + "/" + original.getRiskInfoId();
		String authorization = token(user(Role.ADMIN));
		var response = mvc.perform(get("/auth/csrf")).andReturn().getResponse();
		var csrf = mapper.readTree(response.getContentAsString());
		for (var request : List.of(patch(path), patch(path).cookie(response.getCookie("BOGEUM_CSRF")),
				patch(path).header(csrf.get("headerName").asText(), csrf.get("token").asText()),
				patch(path).cookie(response.getCookie("BOGEUM_CSRF")).header(csrf.get("headerName").asText(), "wrong"))) {
			mvc.perform(request.header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
				.content(body(RiskReason.RENT_OVERDUE, "정정", "확인")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		}
		assertThat(row(original.getRiskInfoId())).isEqualTo(before);
	}

	private void assertRejected(RiskInfo original, String request, int expectedStatus, String code) throws Exception {
		var before = row(original.getRiskInfoId());
		mvc.perform(csrfPatch(original.getRiskInfoId()).header("Authorization", token(user(Role.ADMIN))).content(request))
			.andExpect(status().is(expectedStatus)).andExpect(jsonPath("$.code").value(code));
		assertThat(row(original.getRiskInfoId())).isEqualTo(before);
	}

	private MockHttpServletRequestBuilder csrfPatch(Long id) throws Exception {
		var response = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
		var csrf = mapper.readTree(response.getContentAsString());
		return patch(PATH + "/" + id).contentType(MediaType.APPLICATION_JSON).cookie(response.getCookie("BOGEUM_CSRF"))
			.header(csrf.get("headerName").asText(), csrf.get("token").asText());
	}

	private String body(RiskReason reason, String detail, String updateReason) {
		return mapper.writeValueAsString(new RiskDto.UserUpdateReq(reason, detail, updateReason));
	}

	private String token(User user) {
		return "Bearer " + jwt.createAccess(user.getUserId(), UUID.randomUUID().toString());
	}

	private User user(Role role) {
		return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com").name("정정 대상 " + UUID.randomUUID())
			.passwordHash("must-not-be-exposed").phone("01000000000").role(role).build());
	}

	private RiskInfo risk(User target, RiskReason reason, RiskStatus status) {
		return risks.saveAndFlush(RiskInfo.builder().targetType(RiskTargetType.USER).targetUser(target)
			.reasonType(reason).riskStatus(status).registeredBy(user(Role.ADMIN)).detail("기존 상세")
			.evidenceFileKey("private/original.pdf").updateReason("이전 수정 사유")
			.releasedAt(status == RiskStatus.RELEASED ? LocalDateTime.of(2026, 1, 1, 12, 0) : null).build());
	}

	private Map<String, Object> row(Long id) {
		var row = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
		row.putAll(jdbc.queryForMap("select * from risk_info where risk_info_id = ?", id));
		return row;
	}

	private Map<String, Object> immutableFields(Map<String, Object> row) {
		var immutable = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
		immutable.putAll(row);
		for (String field : List.of("reason_type", "detail", "update_reason", "updated_at")) {
			immutable.remove(field);
		}
		return immutable;
	}
}
