package com.bogeum;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import com.bogeum.common.enums.ApprovalStatus;
import com.bogeum.domain.agent.*;
import com.bogeum.domain.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-demo-approval-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "auth.cookie-secure=false"
})
@ActiveProfiles("demo")
@AutoConfigureMockMvc
@Import(AuthIntegrationTests.AdminAuthCheckController.class)
class DemoAgentApprovalTests {
	private static final String PATH = "/api/v1/agents/me/demo-approval";
	@Autowired private MockMvc mvc;
	@Autowired private AgentService agents;
	@Autowired private AgentRepository repository;
	@Autowired private UserService users;
	@Autowired private JsonMapper mapper;

	@Test
	void approvesOnlySelfAndImmediatelyAllowsBusiness() throws Exception {
		var agent = agent();
		var other = agent();
		var session = users.login(agent.getUser().getEmail(), "demo-password");
		String authorization = "Bearer " + session.getAccessToken();
		mvc.perform(get("/api/v1/agents/me").header("Authorization", authorization))
			.andExpect(status().isOk()).andExpect(jsonPath("$.demoApprovalAvailable").value(true));
		mvc.perform(get("/api/v1/agents/auth-check").header("Authorization", authorization)).andExpect(status().isForbidden());
		mvc.perform(csrfPost().header("Authorization", authorization)
			.contentType("application/json").content("{\"userId\":" + other.getUser().getUserId() + "}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
			.andExpect(jsonPath("$.userId").value(agent.getUser().getUserId()));
		var saved = repository.findById(agent.getAgentProfileId()).orElseThrow();
		assertThat(saved.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
		assertThat(saved.getReviewedAt()).isNotNull();
		assertThat(saved.getReviewedBy()).isNull();
		assertThat(repository.findById(other.getAgentProfileId()).orElseThrow().getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
		mvc.perform(get("/api/v1/agents/auth-check").header("Authorization", authorization)).andExpect(status().isOk());
		mvc.perform(get("/api/v1/auth/me").header("Authorization", authorization))
			.andExpect(status().isOk()).andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
		mvc.perform(csrfPost().header("Authorization", authorization)).andExpect(status().isOk());
		assertThat(repository.findById(agent.getAgentProfileId()).orElseThrow().getReviewedAt()).isEqualTo(saved.getReviewedAt());
	}

	@Test
	void requiresCsrfLoginAndAgentRole() throws Exception {
		var agent = agent();
		var session = users.login(agent.getUser().getEmail(), "demo-password");
		mvc.perform(post(PATH).header("Authorization", "Bearer " + session.getAccessToken()))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		mvc.perform(csrfPost()).andExpect(status().isUnauthorized());
		var user = users.signup(UUID.randomUUID() + "@example.com", "password", "일반회원", "01000000000", true, true);
		var userSession = users.login(user.getEmail(), "password");
		mvc.perform(csrfPost().header("Authorization", "Bearer " + userSession.getAccessToken())).andExpect(status().isForbidden());
		assertThat(repository.findById(agent.getAgentProfileId()).orElseThrow().getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
	}

	@Test
	void demoApprovalBeansAreExcludedWhenProdIsActive() {
		new ApplicationContextRunner()
			.withInitializer(context -> context.getEnvironment().setActiveProfiles("demo", "prod"))
			.withUserConfiguration(DemoAgentController.class, DemoAgentService.class)
			.run(context -> {
				assertThat(context).doesNotHaveBean(DemoAgentController.class);
				assertThat(context).doesNotHaveBean(DemoAgentService.class);
			});
	}

	private com.bogeum.domain.agent.model.Agent agent() {
		return agents.signup(UUID.randomUUID() + "@example.com", "demo-password", "대표", "01000000000",
			true, true, "실습 중개사", "서울 테스트로 1");
	}

	private MockHttpServletRequestBuilder csrfPost() throws Exception {
		var response = mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse();
		var csrf = mapper.readTree(response.getContentAsString());
		return post(PATH).cookie(response.getCookie("BOGEUM_CSRF"))
			.header(csrf.get("headerName").asText(), csrf.get("token").asText());
	}
}
