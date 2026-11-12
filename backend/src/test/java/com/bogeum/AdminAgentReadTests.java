package com.bogeum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bogeum.common.enums.ApprovalStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.agent.AgentRepository;
import com.bogeum.domain.agent.model.Agent;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 실제 JWT 필터와 H2 조회를 연결해 관리자 목록 계약을 검증한다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-admin-agent-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "auth.cookie-secure=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AdminAgentReadTests {
	private static final String PATH = "/admin/agents";
	private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 11, 1, 12, 0);
	@Autowired private MockMvc mvc;
	@Autowired private AgentRepository agents;
	@Autowired private UserRepository users;
	@Autowired private JwtUtil jwt;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private jakarta.persistence.EntityManager entityManager;

	@Test
	void pendingComesFirstThenNewestAndIdBreaksTies() throws Exception {
		var approved = agent("승인 사무소", "서울", ApprovalStatus.APPROVED, JOINED_AT.plusDays(5));
		var first = agent("신규 신청1", "서울", ApprovalStatus.PENDING, JOINED_AT);
		var second = agent("신규 신청2", "서울", ApprovalStatus.PENDING, JOINED_AT);
		var older = agent("과거 신청", "서울", ApprovalStatus.PENDING, JOINED_AT.minusDays(1));
		var rejected = agent("반려 사무소", "서울", ApprovalStatus.REJECTED, JOINED_AT.plusDays(4));
		mvc.perform(get(PATH).header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.agents[0].agentId").value(second.getAgentProfileId()))
			.andExpect(jsonPath("$.agents[1].agentId").value(first.getAgentProfileId()))
			.andExpect(jsonPath("$.agents[2].agentId").value(older.getAgentProfileId()))
			.andExpect(jsonPath("$.agents[3].agentId").value(approved.getAgentProfileId()))
			.andExpect(jsonPath("$.agents[4].agentId").value(rejected.getAgentProfileId()))
			.andExpect(jsonPath("$.agents[0].approvalStatus").value("PENDING"))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.totalElements").value(5));
	}

	@Test
	void searchesOfficeNameOrAddressIgnoringCaseAndOuterSpaces() throws Exception {
		agent("Bogeum Realty", "서울 강남구", ApprovalStatus.APPROVED, JOINED_AT);
		agent("다른 사무소", "서울 송파구", ApprovalStatus.REJECTED, JOINED_AT);
		String admin = token(Role.ADMIN);
		mvc.perform(get(PATH).param("keyword", "  bOgEuM  ").header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.agents[0].officeName").value("Bogeum Realty"));
		mvc.perform(get(PATH).param("keyword", "송파").header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.agents[0].approvalStatus").value("REJECTED"));
		mvc.perform(get(PATH).param("keyword", "대표자").header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.agents").isEmpty());
	}

	@Test
	void searchTreatsWildcardCharactersAsLiteralText() throws Exception {
		agent("100%_보금", "서울", ApprovalStatus.PENDING, JOINED_AT);
		agent("일반 사무소", "서울", ApprovalStatus.APPROVED, JOINED_AT);
		mvc.perform(get(PATH).param("keyword", "%_").header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.agents[0].officeName").value("100%_보금"));
	}

	@Test
	void paginatesInDatabaseAndPreservesFilteredTotal() throws Exception {
		for (int i = 0; i < 7; i++) {
			agent("보금" + i, "서울", ApprovalStatus.PENDING, JOINED_AT.plusDays(i));
		}
		agent("검색 제외", "부산", ApprovalStatus.APPROVED, JOINED_AT);
		String admin = token(Role.ADMIN);
		mvc.perform(get(PATH).param("keyword", "보금").header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.agents.length()").value(5))
			.andExpect(jsonPath("$.agents[0].officeName").value("보금6"));
		mvc.perform(get(PATH).param("keyword", "보금").param("page", "2").header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.agents.length()").value(2))
			.andExpect(jsonPath("$.agents[0].officeName").value("보금1"))
			.andExpect(jsonPath("$.page").value(2)).andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.totalElements").value(7));
		mvc.perform(get(PATH).param("keyword", "보금").param("page", "3").header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.agents").isEmpty())
			.andExpect(jsonPath("$.totalElements").value(7));
	}

	@Test
	void emptyAndBlankSearchReturnConsistentMetadata() throws Exception {
		String admin = token(Role.ADMIN);
		mvc.perform(get(PATH).header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.agents").isEmpty())
			.andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.totalPages").value(0))
			.andExpect(jsonPath("$.totalElements").value(0));
		agent("보금", "서울", ApprovalStatus.PENDING, JOINED_AT);
		mvc.perform(get(PATH).param("keyword", "   ").header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(get(PATH).param("keyword", "없는 주소").header("Authorization", admin))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void excludesAccountAndDocumentDetails() throws Exception {
		agent("보금", "서울", ApprovalStatus.APPROVED, JOINED_AT);
		var result = mvc.perform(get(PATH).header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.agents[0].representativeName").value("대표자"))
			.andExpect(jsonPath("$.agents[0].createdAt").exists()).andReturn();
		assertThat(result.getResponse().getContentAsString())
			.doesNotContain("passwordHash", "email", "phone", "fileKey", "reviewedBy", "rejectReason");
	}

	@Test
	void requiresAuthenticationAndAdminRoleEvenForApprovedAgent() throws Exception {
		mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
		mvc.perform(get(PATH).header("Authorization", "Bearer invalid"))
			.andExpect(status().isUnauthorized());
		mvc.perform(get(PATH).header("Authorization", token(Role.USER)))
			.andExpect(status().isForbidden());
		var approved = agent("승인 사무소", "서울", ApprovalStatus.APPROVED, JOINED_AT);
		mvc.perform(get(PATH).header("Authorization", "Bearer " + jwt.createAccess(
			approved.getUser().getUserId(), UUID.randomUUID().toString())))
			.andExpect(status().isForbidden());
		mvc.perform(get(PATH).header("Authorization", token(Role.AGENT)))
			.andExpect(status().isForbidden());
	}

	@Test
	void followsExistingPageNormalizationAndRejectsNonNumericPage() throws Exception {
		String admin = token(Role.ADMIN);
		for (String page : java.util.List.of("0", "-1")) {
			mvc.perform(get(PATH).param("page", page).header("Authorization", admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1));
		}
		mvc.perform(get(PATH).param("page", "abc").header("Authorization", admin))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@ParameterizedTest
	@EnumSource(value = Role.class, names = {"ADMIN", "USER", "AGENT"})
	void appliesSameAdminAuthorizationToGetAndHead(Role role) throws Exception {
		String authorization = token(role);
		int expectedStatus = role == Role.ADMIN ? 200 : 403;
		mvc.perform(get(PATH).header("Authorization", authorization))
			.andExpect(status().is(expectedStatus));
		mvc.perform(head(PATH).header("Authorization", authorization))
			.andExpect(status().is(expectedStatus));
	}

	@Test
	void headRequiresValidAuthentication() throws Exception {
		mvc.perform(head(PATH)).andExpect(status().isUnauthorized());
		mvc.perform(head(PATH).header("Authorization", "Bearer invalid"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void acceptsLargestPageWhoseOffsetFitsJpaIntegerRange() throws Exception {
		// 공개 계약인 5건/페이지에서 int 오프셋을 넘지 않는 마지막 페이지다.
		int lastPage = 429496730;
		agent("보금", "서울", ApprovalStatus.PENDING, JOINED_AT);
		mvc.perform(get(PATH).param("page", Integer.toString(lastPage))
			.header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.agents").isEmpty())
			.andExpect(jsonPath("$.page").value(lastPage))
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@ParameterizedTest
	@ValueSource(strings = {"429496731", "2147483647", "2147483648"})
	void rejectsOffsetOverflowAndPageBindingOverflowAsBadRequest(String page) throws Exception {
		mvc.perform(get(PATH).param("page", page).header("Authorization", token(Role.ADMIN)))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	private String token(Role role) {
		return "Bearer " + jwt.createAccess(user(role).getUserId(), UUID.randomUUID().toString());
	}

	private User user(Role role) {
		return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
			.passwordHash("must-not-be-exposed").name("대표자").phone("01000000000").role(role).build());
	}

	private Agent agent(String officeName, String address, ApprovalStatus status, LocalDateTime createdAt) {
		var agent = agents.saveAndFlush(Agent.builder().user(user(Role.AGENT)).officeName(officeName)
			.officeAddress(address).approvalStatus(status).build());
		// Hibernate 자동 생성 시각 대신 정렬 경계를 명확하게 고정한다.
		jdbc.update("update agent_profile set created_at = ? where agent_profile_id = ?",
			createdAt, agent.getAgentProfileId());
		entityManager.clear();
		return agent;
	}
}
