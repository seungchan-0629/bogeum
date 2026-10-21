package com.bogeum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bogeum.repository.MemberRepository;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:production-registration;DB_CLOSE_DELAY=-1",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"bogeum.registration.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProductionRegistrationTests {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private ApplicationContext context;
	@Autowired private MemberRepository members;

	@Test
	void developmentIdentityEndpointAndServiceAreAbsentInProduction() throws Exception {
		assertThat(context.containsBean("devIdentityVerificationController")).isFalse();
		assertThat(context.containsBean("devIdentityVerificationService")).isFalse();
		assertThat(context.containsBean("unavailableIdentityVerificationService")).isTrue();
		mockMvc.perform(post("/api/dev/identity-verifications").with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void draftTermsCannotBeUsedForProductionSignupEvenWhenEnabledByConfiguration() throws Exception {
		mockMvc.perform(get("/api/terms").param("memberType", "AGENT"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.registrationAvailable").value(false));
		Map<String, Object> body = Map.of(
			"account", Map.of("email", "prod@example.com", "password", "Test-password-2026!",
				"passwordConfirm", "Test-password-2026!"),
			"phoneNumber", "010-1234-5678",
			"office", Map.of("name", "테스트 사무소", "address", "서울시 테스트로", "phoneNumber", "02-123-4567"),
			"consents", List.of(
				Map.of("type", "PRIVACY_COLLECTION", "version", "agent-dev-v1", "agreed", true),
				Map.of("type", "THIRD_PARTY_PROVISION", "version", "agent-dev-v1", "agreed", true)));
		mockMvc.perform(post("/api/auth/signup/agent").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(body))).andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("REGISTRATION_UNAVAILABLE"));
		assertThat(members.count()).isZero();
	}
}
