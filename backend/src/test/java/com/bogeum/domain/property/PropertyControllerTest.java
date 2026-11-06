package com.bogeum.domain.property;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.bogeum.common.enums.BrokerageStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.property.model.PropertyDto;
import com.bogeum.domain.user.model.User;
import com.bogeum.domain.user.model.UserDto.LoginUser;

/** 중개 신청 요청의 JSON 변환·검증을 DB 없이 확인한다. 권한 검사는 Security 설정이 맡는다. */
@WebMvcTest(PropertyController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PropertyControllerTest.PrincipalResolverConfig.class)
class PropertyControllerTest {

	// 웹 슬라이스 테스트에는 @AuthenticationPrincipal 처리기가 없어 직접 등록한다.
	@TestConfiguration
	static class PrincipalResolverConfig implements WebMvcConfigurer {
		@Override
		public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
			resolvers.add(new AuthenticationPrincipalArgumentResolver());
		}
	}

	private static final String URL = "/property/brokerage-requests";
	private static final String VALID_REQUEST = """
		{"leaseType":"MONTHLY","deposit":10000000,"monthlyRent":700000,
		 "roadAddress":"서울특별시 마포구 월드컵로 61","detailAddress":"302호","agentId":3}
		""";

	private final MockMvc mockMvc;

	@MockitoBean
	private PropertyService propertyService;

	@Autowired
	PropertyControllerTest(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	// 필터를 끈 테스트라 JWT 필터 대신 로그인 사용자를 직접 넣는다.
	@BeforeEach
	void login() {
		var principal = new LoginUser(User.builder().userId(1L).email("user@example.com").passwordHash("hash")
			.name("임대인").role(Role.USER).build());
		SecurityContextHolder.getContext().setAuthentication(
			UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
	}

	@AfterEach
	void logout() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("중개 신청은 로그인 사용자 기준으로 처리하고 201과 배정 결과를 반환한다")
	void createsBrokerageRequest() throws Exception {
		when(propertyService.createBrokerageRequest(eq(1L), any(PropertyDto.BrokerageRequest.class)))
			.thenReturn(PropertyDto.BrokerageResponse.builder().brokerageRequestId(10L).propertyId(20L).agentId(3L)
				.officeName("망원 행복공인중개사사무소").isDesignated(true).brokerageStatus(BrokerageStatus.REQUESTED).build());

		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.brokerageRequestId").value(10))
			.andExpect(jsonPath("$.agentId").value(3))
			.andExpect(jsonPath("$.isDesignated").value(true))
			.andExpect(jsonPath("$.brokerageStatus").value("REQUESTED"));
	}

	@Test
	@DisplayName("중개 신청 수락은 로그인 중개사 기준으로 처리하고 수락 결과를 반환한다")
	void acceptsBrokerageRequest() throws Exception {
		when(propertyService.acceptBrokerageRequest(1L, 10L)).thenReturn(PropertyDto.AcceptResponse.builder()
			.brokerageRequestId(10L).propertyId(20L).brokerageStatus(BrokerageStatus.ACCEPTED).build());

		mockMvc.perform(post(URL + "/10/accept"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.brokerageRequestId").value(10))
			.andExpect(jsonPath("$.propertyId").value(20))
			.andExpect(jsonPath("$.brokerageStatus").value("ACCEPTED"));
	}

	@Test
	@DisplayName("중개 신청 목록은 로그인 중개사·상태·페이지로 조회하고 목록과 페이지 정보를 반환한다")
	void readsBrokerageRequests() throws Exception {
		when(propertyService.readBrokerageRequests(1L, BrokerageStatus.ACCEPTED, 2)).thenReturn(
			PropertyDto.BrokerageListResponse.builder()
				.requests(List.of(PropertyDto.BrokerageItemResponse.builder().brokerageRequestId(10L)
					.brokerageStatus(BrokerageStatus.ACCEPTED).build()))
				.page(2).totalPages(3).totalElements(12).build());

		mockMvc.perform(get(URL + "/received").param("status", "ACCEPTED").param("page", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.requests[0].brokerageRequestId").value(10))
			.andExpect(jsonPath("$.requests[0].brokerageStatus").value("ACCEPTED"))
			.andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.totalPages").value(3))
			.andExpect(jsonPath("$.totalElements").value(12));
	}

	@Test
	@DisplayName("중개 신청 목록은 page가 없으면 첫 페이지를 조회한다")
	void readsFirstPageByDefault() throws Exception {
		when(propertyService.readBrokerageRequests(1L, BrokerageStatus.REQUESTED, 1)).thenReturn(
			PropertyDto.BrokerageListResponse.builder().requests(List.of()).page(1).build());

		mockMvc.perform(get(URL + "/received").param("status", "REQUESTED"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(1));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "?status=WRONG", "?status=REQUESTED&page=abc"})
	@DisplayName("중개 신청 목록은 상태가 없거나 틀리거나 페이지가 숫자가 아니면 400으로 거절한다")
	void rejectsInvalidListRequest(String query) throws Exception {
		mockMvc.perform(get(URL + "/received" + query))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		verifyNoInteractions(propertyService);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{}",
		"{\"deposit\":10000000,\"roadAddress\":\"서울특별시 마포구\"}",
		"{\"leaseType\":\"MONTHLY\",\"roadAddress\":\"서울특별시 마포구\"}",
		"{\"leaseType\":\"MONTHLY\",\"deposit\":-1,\"roadAddress\":\"서울특별시 마포구\"}",
		"{\"leaseType\":\"MONTHLY\",\"deposit\":0,\"monthlyRent\":-1,\"roadAddress\":\"서울특별시 마포구\"}",
		"{\"leaseType\":\"MONTHLY\",\"deposit\":0,\"roadAddress\":\"  \"}",
		"{\"leaseType\":\"SALE\",\"deposit\":0,\"roadAddress\":\"서울특별시 마포구\"}"
	})
	@DisplayName("필수값 누락·음수 금액·없는 임대 형식은 서비스 호출 전에 400으로 거절한다")
	void rejectsInvalidRequest(String body) throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		verifyNoInteractions(propertyService);
	}
}
