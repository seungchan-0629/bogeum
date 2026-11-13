package com.bogeum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.bogeum.common.enums.RiskReason;
import com.bogeum.common.enums.RiskStatus;
import com.bogeum.common.enums.RiskTargetType;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.risk.RiskRepository;
import com.bogeum.domain.risk.model.RiskInfo;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.JwtUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** #34와 같은 실제 JWT 필터·H2 조회로 관리자 위험정보 목록 계약을 검증한다. */
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
class AdminRiskUserReadTests {
	private static final String PATH = "/admin/risk-users";
	private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 1, 12, 0);
	@Autowired private MockMvc mvc;
	@Autowired private RiskRepository risks;
	@Autowired private UserRepository users;
	@Autowired private JwtUtil jwt;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private jakarta.persistence.EntityManager entityManager;

	@Test
	void returnsRegisteredUserRisksOnly() throws Exception {
		var target = user("대상", Role.USER);
		var included = risk(target, RiskReason.RENT_UNPAID, RiskStatus.REGISTERED, CREATED_AT, null);
		risk(target, RiskReason.RENT_UNPAID, RiskStatus.CANDIDATE, CREATED_AT, null);
		risk(target, RiskReason.RENT_UNPAID, RiskStatus.RELEASED, CREATED_AT, null);
		risk(target, RiskReason.MORTGAGE_RISK, RiskStatus.REGISTERED, CREATED_AT, null);
		risks.saveAndFlush(RiskInfo.builder().targetType(RiskTargetType.PROPERTY).targetUser(target)
			.reasonType(RiskReason.RENT_UNPAID).riskStatus(RiskStatus.REGISTERED).build());
		// 기존 nullable 연관관계에 사용자 없는 정보가 있어도 목록에 포함하지 않는다.
		risks.saveAndFlush(RiskInfo.builder().targetType(RiskTargetType.USER)
			.reasonType(RiskReason.RENT_UNPAID).riskStatus(RiskStatus.REGISTERED).build());
		mvc.perform(get(PATH).header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.riskUsers[0].riskInfoId").value(included.getRiskInfoId()));
	}

	@ParameterizedTest
	@CsvSource({
		"RENT_UNPAID,TENANT", "RENT_OVERDUE,TENANT",
		"DEPOSIT_UNPAID,LANDLORD", "FALSE_LISTING,LANDLORD",
		"NO_PROPERTY_IMAGE,AGENT", "AGENT_CHANGE_HISTORY,AGENT"
	})
	void classifiesAndFiltersEachReasonIndependentlyOfAccountRole(RiskReason reason, String type) throws Exception {
		// AGENT 계정에도 임대인·임차인 사유가 있을 수 있다. 계정 역할로 분류하지 않는다.
		var target = user("대상", Role.AGENT);
		var included = risk(target, reason, RiskStatus.REGISTERED, CREATED_AT, null);
		RiskReason otherReason = type.equals("TENANT") ? RiskReason.FALSE_LISTING : RiskReason.RENT_UNPAID;
		risk(target, otherReason, RiskStatus.REGISTERED, CREATED_AT.plusDays(1), null);
		mvc.perform(get(PATH).param("type", type).header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.riskUsers[0].riskInfoId").value(included.getRiskInfoId()))
			.andExpect(jsonPath("$.riskUsers[0].userType").value(type))
			.andExpect(jsonPath("$.riskUsers[0].reasonType").value(reason.name()));
	}

	@Test
	void keepsSeparateRowsForSameUserAcrossTypesAndReasons() throws Exception {
		var target = user("대상", Role.USER);
		for (RiskReason reason : List.of(RiskReason.RENT_UNPAID, RiskReason.RENT_OVERDUE,
				RiskReason.FALSE_LISTING, RiskReason.AGENT_CHANGE_HISTORY)) {
			risk(target, reason, RiskStatus.REGISTERED, CREATED_AT, null);
		}
		mvc.perform(get(PATH).header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4))
			.andExpect(jsonPath("$.riskUsers.length()").value(4))
			.andExpect(jsonPath("$.riskUsers[*].userId", org.hamcrest.Matchers.everyItem(
				org.hamcrest.Matchers.is(target.getUserId().intValue()))))
			.andExpect(jsonPath("$.riskUsers[*].userType", org.hamcrest.Matchers.containsInAnyOrder(
				"TENANT", "TENANT", "LANDLORD", "AGENT")));
	}

	@Test
	void sortsByCreationTimeThenIdDescending() throws Exception {
		var target = user("대상", Role.USER);
		var first = risk(target, RiskReason.RENT_UNPAID, RiskStatus.REGISTERED, CREATED_AT, null);
		var second = risk(target, RiskReason.RENT_OVERDUE, RiskStatus.REGISTERED, CREATED_AT, null);
		var older = risk(target, RiskReason.FALSE_LISTING, RiskStatus.REGISTERED, CREATED_AT.minusDays(1), null);
		var newest = risk(target, RiskReason.NO_PROPERTY_IMAGE, RiskStatus.REGISTERED, CREATED_AT.plusDays(1), null);
		mvc.perform(get(PATH).header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.riskUsers[0].riskInfoId").value(newest.getRiskInfoId()))
			.andExpect(jsonPath("$.riskUsers[1].riskInfoId").value(second.getRiskInfoId()))
			.andExpect(jsonPath("$.riskUsers[2].riskInfoId").value(first.getRiskInfoId()))
			.andExpect(jsonPath("$.riskUsers[3].riskInfoId").value(older.getRiskInfoId()));
	}

	@Test
	void searchesNameOnlyIgnoringCaseAndOuterSpaces() throws Exception {
		risk(user("Bogeum 사용자", Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED, CREATED_AT, null);
		risk(user("다른 사용자", Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED, CREATED_AT, null);
		String admin = token(Role.ADMIN);
		mvc.perform(get(PATH).param("keyword", "  bOgEuM  ").header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.riskUsers[0].name").value("Bogeum 사용자"));
		mvc.perform(get(PATH).param("keyword", "example.com").header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.riskUsers").isEmpty());
		mvc.perform(get(PATH).param("keyword", "관리자 확인용 상세 사유").header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.riskUsers").isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"%", "_", "%_"})
	void treatsWildcardCharactersAsLiteralText(String keyword) throws Exception {
		risk(user("100%_보금", Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED, CREATED_AT, null);
		risk(user("일반 이름", Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED, CREATED_AT, null);
		mvc.perform(get(PATH).param("keyword", keyword).header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.riskUsers[0].name").value("100%_보금"));
	}

	@Test
	void paginatesCombinedFiltersInDatabaseAndPreservesTotal() throws Exception {
		var target = user("보금 대상", Role.USER);
		for (int i = 0; i < 7; i++) {
			risk(target, RiskReason.RENT_UNPAID, RiskStatus.REGISTERED, CREATED_AT.plusDays(i), null);
		}
		risk(target, RiskReason.FALSE_LISTING, RiskStatus.REGISTERED, CREATED_AT, null);
		risk(target, RiskReason.RENT_UNPAID, RiskStatus.CANDIDATE, CREATED_AT, null);
		risk(user("이름 제외", Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED, CREATED_AT, null);
		String admin = token(Role.ADMIN);
		mvc.perform(get(PATH).param("type", "TENANT").param("keyword", " 보금 ").header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.riskUsers.length()").value(5))
			.andExpect(jsonPath("$.totalElements").value(7))
			.andExpect(jsonPath("$.riskUsers[0].createdAt").value("2026-11-07T12:00:00"));
		mvc.perform(get(PATH).param("type", "TENANT").param("keyword", "보금").param("page", "2")
			.header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.riskUsers.length()").value(2))
			.andExpect(jsonPath("$.riskUsers[0].createdAt").value("2026-11-02T12:00:00"))
			.andExpect(jsonPath("$.page").value(2)).andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.totalElements").value(7));
		mvc.perform(get(PATH).param("type", "TENANT").param("keyword", "보금").param("page", "3")
			.header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.riskUsers").isEmpty())
			.andExpect(jsonPath("$.page").value(3)).andExpect(jsonPath("$.totalElements").value(7));
	}

	@Test
	void returnsConsistentMetadataForEmptyResultsAndBlankSearch() throws Exception {
		String admin = token(Role.ADMIN);
		mvc.perform(get(PATH).header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.riskUsers").isEmpty())
			.andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.totalPages").value(0))
			.andExpect(jsonPath("$.totalElements").value(0));
		risk(user("보금", Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED, CREATED_AT, null);
		mvc.perform(get(PATH).param("keyword", "   ").param("type", "").header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(get(PATH).param("keyword", "없는 이름").header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.riskUsers").isEmpty())
			.andExpect(jsonPath("$.totalPages").value(0)).andExpect(jsonPath("$.totalElements").value(0));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", "\t\n", "risk/private/missing-object.pdf"})
	void exposesOnlyEvidenceKeyPresenceAndMinimalListFields(String key) throws Exception {
		var target = user("보금", Role.USER);
		var saved = risk(target, RiskReason.RENT_UNPAID, RiskStatus.REGISTERED, CREATED_AT, key);
		var result = mvc.perform(get(PATH).header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.riskUsers[0].riskInfoId").value(saved.getRiskInfoId()))
			.andExpect(jsonPath("$.riskUsers[0].userId").value(target.getUserId()))
			.andExpect(jsonPath("$.riskUsers[0].name").value("보금"))
			.andExpect(jsonPath("$.riskUsers[0].createdAt").value("2026-11-01T12:00:00"))
			.andExpect(jsonPath("$.riskUsers[0].hasEvidence").value(key != null && !key.isBlank()))
			.andExpect(jsonPath("$.riskUsers[0].*", org.hamcrest.Matchers.hasSize(8))).andReturn();
		assertThat(result.getResponse().getContentAsString())
			.doesNotContain("passwordHash", "email", "phone", "address", "evidenceFileKey",
				"risk/private/", "must-not-be-exposed", "registeredBy", "updateReason", "releasedAt",
				"updatedAt", "evidenceUrl", "downloadUrl");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"8월분 미납, 58일 연체", "  첫 줄\n둘째 줄  "})
	void returnsStoredDetailIncludingNull(String detail) throws Exception {
		var saved = risks.saveAndFlush(RiskInfo.builder().targetType(RiskTargetType.USER)
			.targetUser(user("보금", Role.USER)).reasonType(RiskReason.RENT_UNPAID)
			.riskStatus(RiskStatus.REGISTERED).detail(detail).build());
		entityManager.clear();
		mvc.perform(get(PATH).header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.riskUsers[0].riskInfoId").value(saved.getRiskInfoId()))
			.andExpect(jsonPath("$.riskUsers[0]", org.hamcrest.Matchers.hasKey("detail")))
			.andExpect(jsonPath("$.riskUsers[0].detail").value(org.hamcrest.Matchers.is(detail)));
	}

	@ParameterizedTest
	@EnumSource(value = Role.class, names = {"ADMIN", "USER", "AGENT"})
	void appliesAdminAuthorizationToGetAndHead(Role role) throws Exception {
		String authorization = token(role);
		int expectedStatus = role == Role.ADMIN ? 200 : 403;
		mvc.perform(get(PATH).header("Authorization", authorization))
			.andExpect(status().is(expectedStatus));
		mvc.perform(head(PATH).header("Authorization", authorization))
			.andExpect(status().is(expectedStatus));
	}

	@Test
	void rejectsUnauthenticatedAndInvalidTokenForGetAndHead() throws Exception {
		mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
		mvc.perform(head(PATH)).andExpect(status().isUnauthorized());
		mvc.perform(get(PATH).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
		mvc.perform(head(PATH).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
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
		risk(user("대상", Role.USER), RiskReason.RENT_UNPAID, RiskStatus.REGISTERED, CREATED_AT, null);
		mvc.perform(get(PATH).param("page", "429496730").header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.riskUsers").isEmpty())
			.andExpect(jsonPath("$.page").value(429496730)).andExpect(jsonPath("$.totalElements").value(1));
	}

	@ParameterizedTest
	@ValueSource(strings = {"USER", "PROPERTY", "ALL", "tenant", " TENANT ", " "})
	void rejectsUnknownTypes(String type) throws Exception {
		mvc.perform(get(PATH).param("type", type).header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	private String token(Role role) {
		return "Bearer " + jwt.createAccess(user("인증 사용자", role).getUserId(), UUID.randomUUID().toString());
	}

	private User user(String name, Role role) {
		return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
			.passwordHash("must-not-be-exposed").name(name).phone("01000000000")
			.address("노출 금지 주소").role(role).build());
	}

	private RiskInfo risk(User target, RiskReason reason, RiskStatus status, LocalDateTime createdAt, String key) {
		var risk = risks.saveAndFlush(RiskInfo.builder().targetType(RiskTargetType.USER).targetUser(target)
			.reasonType(reason).riskStatus(status).evidenceFileKey(key).detail("관리자 확인용 상세 사유").build());
		// 생성 시각과 ID가 역순인 경우 및 동률 정렬 경계를 명확하게 고정한다.
		jdbc.update("update risk_info set created_at = ? where risk_info_id = ?", createdAt, risk.getRiskInfoId());
		entityManager.clear();
		return risk;
	}
}
