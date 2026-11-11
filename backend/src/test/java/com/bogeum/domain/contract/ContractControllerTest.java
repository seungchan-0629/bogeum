package com.bogeum.domain.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.contract.model.ContractDto;
import com.bogeum.domain.property.PropertyException;
import com.bogeum.domain.user.model.User;
import com.bogeum.domain.user.model.UserDto.LoginUser;

/** 계약 요청의 변환·검증을 DB 없이 확인한다. 권한 검사는 Security 설정이 맡는다. */
@WebMvcTest(ContractController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ContractControllerTest.PrincipalResolverConfig.class)
class ContractControllerTest {

	// 웹 슬라이스 테스트에는 @AuthenticationPrincipal 처리기가 없어 직접 등록한다.
	@TestConfiguration
	static class PrincipalResolverConfig implements WebMvcConfigurer {
		@Override
		public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
			resolvers.add(new AuthenticationPrincipalArgumentResolver());
		}
	}

	private static final String VALID_REQUEST = """
		{"propertyId":20,"tenantId":2,"deposit":9500000,"monthlyRent":600000,"paymentDay":25,
		 "startAt":"2026-12-01","endAt":"2028-11-30","contractedAt":"2026-11-08","brokerageFee":300000}
		""";

	private final MockMvc mockMvc;

	@MockitoBean
	private ContractService contractService;

	@Autowired
	ContractControllerTest(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	// 필터를 끈 테스트라 JWT 필터 대신 로그인 중개사를 직접 넣는다.
	@BeforeEach
	void login() {
		var principal = new LoginUser(User.builder().userId(101L).email("agent@example.com").passwordHash("hash")
			.name("중개사").role(Role.AGENT).build());
		SecurityContextHolder.getContext().setAuthentication(
			UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
	}

	@AfterEach
	void logout() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("계약 등록은 로그인 중개사 기준으로 계약 정보와 계약서 이미지를 넘기고 201과 계약 번호를 반환한다")
	void createsContract() throws Exception {
		var request = ArgumentCaptor.forClass(ContractDto.ContractRequest.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<MultipartFile>> images = ArgumentCaptor.forClass(List.class);
		when(contractService.createContract(eq(101L), request.capture(), images.capture())).thenReturn(
			ContractDto.SaveResponse.builder().contractId(30L).propertyId(20L).propertyStatus(PropertyStatus.CONTRACTED)
				.build());

		mockMvc.perform(multipart("/contract-details").file(requestPart(VALID_REQUEST))
				.file(new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[]{1}))
				.file(new MockMultipartFile("images", "b.jpg", "image/jpeg", new byte[]{2})))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.contractId").value(30))
			.andExpect(jsonPath("$.propertyStatus").value("CONTRACTED"));

		assertEquals(2L, request.getValue().getTenantId());
		assertEquals(LocalDate.of(2026, 12, 1), request.getValue().getStartAt());
		assertEquals(LocalDate.of(2026, 11, 8), request.getValue().getContractedAt());
		assertEquals(new BigDecimal("300000"), request.getValue().getBrokerageFee());
		assertEquals(2, images.getValue().size());
	}

	@Test
	@DisplayName("계약서 이미지 파트가 없어도 서비스에 넘겨 사진 오류로 판단하게 한다")
	void passesMissingImagesToService() throws Exception {
		when(contractService.createContract(eq(101L), any(ContractDto.ContractRequest.class), isNull()))
			.thenThrow(ContractException.invalidImage());

		mockMvc.perform(multipart("/contract-details").file(requestPart(VALID_REQUEST)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
	}

	@Test
	@DisplayName("후보가 아닌 임차인이면 400과 INVALID_CONTRACT_TENANT를 반환한다")
	void rejectsTenantOutsideCandidates() throws Exception {
		when(contractService.createContract(eq(101L), any(ContractDto.ContractRequest.class), any()))
			.thenThrow(ContractException.invalidTenant());

		mockMvc.perform(multipart("/contract-details").file(requestPart(VALID_REQUEST))
				.file(new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[]{1})))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CONTRACT_TENANT"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{\"propertyId\":20,\"deposit\":9500000,\"startAt\":\"2026-12-01\",\"endAt\":\"2028-11-30\",\"contractedAt\":\"2026-11-08\",\"brokerageFee\":300000}",
		"{\"propertyId\":20,\"tenantId\":2,\"deposit\":9500000.5,\"startAt\":\"2026-12-01\",\"endAt\":\"2028-11-30\",\"contractedAt\":\"2026-11-08\",\"brokerageFee\":300000}",
		"{\"propertyId\":20,\"tenantId\":2,\"deposit\":9500000,\"paymentDay\":32,\"startAt\":\"2026-12-01\",\"endAt\":\"2028-11-30\",\"contractedAt\":\"2026-11-08\",\"brokerageFee\":300000}",
		"{\"propertyId\":20,\"tenantId\":2,\"deposit\":9500000,\"startAt\":\"2026-12-01\",\"endAt\":\"2028-11-30\",\"brokerageFee\":300000}",
		"{\"propertyId\":20,\"tenantId\":2,\"deposit\":9500000,\"startAt\":\"12/01\",\"endAt\":\"2028-11-30\",\"contractedAt\":\"2026-11-08\",\"brokerageFee\":300000}",
		"{\"propertyId\":20,\"tenantId\":2,\"deposit\":9500000,\"startAt\":\"2026-12-01\",\"endAt\":\"2028-11-30\",\"contractedAt\":\"2026-11-08\",\"brokerageFee\":-1}"
	})
	@DisplayName("임차인·계약일 누락, 원 단위가 아닌 금액, 범위 밖 납부일, 날짜 형식 오류, 음수 보수는 서비스 호출 전에 400으로 거절한다")
	void rejectsInvalidContractRequest(String body) throws Exception {
		mockMvc.perform(multipart("/contract-details").file(requestPart(body)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		verifyNoInteractions(contractService);
	}

	@Test
	@DisplayName("계약 당사자 후보는 로그인 중개사·매물 기준으로 조회하고 임대인·임차인 후보와 신청 가격을 반환한다")
	void readsContractCandidates() throws Exception {
		when(contractService.readContractCandidates(101L, 20L)).thenReturn(ContractDto.CandidateResponse.builder()
			.propertyId(20L).leaseType(LeaseType.MONTHLY).deposit(new BigDecimal("10000000"))
			.monthlyRent(new BigDecimal("650000")).propertyStatus(PropertyStatus.ACTIVE)
			.landlord(ContractDto.PartyResponse.builder().userId(1L).name("박임대").maskedEmail("pa****@example.com").build())
			.tenants(List.of(ContractDto.PartyResponse.builder().userId(2L).name("최세입")
				.maskedEmail("se****@example.com").build()))
			.build());

		mockMvc.perform(get("/contract/candidates").param("propertyId", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.propertyId").value(20))
			.andExpect(jsonPath("$.deposit").value(10000000))
			.andExpect(jsonPath("$.landlord.name").value("박임대"))
			.andExpect(jsonPath("$.tenants[0].userId").value(2))
			.andExpect(jsonPath("$.tenants[0].maskedEmail").value("se****@example.com"));
	}

	@Test
	@DisplayName("없거나 맡지 않은 매물이면 404와 PROPERTY_NOT_FOUND를 반환한다")
	void rejectsMissingProperty() throws Exception {
		when(contractService.readContractCandidates(101L, 999L)).thenThrow(PropertyException.propertyNotFound());

		mockMvc.perform(get("/contract/candidates").param("propertyId", "999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROPERTY_NOT_FOUND"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "?propertyId=abc"})
	@DisplayName("매물 번호가 없거나 숫자가 아니면 서비스 호출 전에 400으로 거절한다")
	void rejectsInvalidPropertyId(String query) throws Exception {
		mockMvc.perform(get("/contract/candidates" + query))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(contractService);
	}

	// 프론트는 계약 정보를 JSON 파일 파트로 보낸다
	private static MockMultipartFile requestPart(String json) {
		return new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
			json.getBytes(StandardCharsets.UTF_8));
	}
}
