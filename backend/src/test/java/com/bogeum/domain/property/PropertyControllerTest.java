package com.bogeum.domain.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
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

import com.bogeum.common.enums.BrokerageStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.PropertyType;
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
	@DisplayName("사진 추가는 로그인 중개사·매물 기준으로 multipart 사진을 넘기고 201과 추가한 사진을 반환한다")
	void createsPropertyImages() throws Exception {
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<MultipartFile>> images = ArgumentCaptor.forClass(List.class);
		when(propertyService.createPropertyImages(eq(1L), eq(20L), images.capture())).thenReturn(List.of(
			PropertyDto.ImageResponse.builder().propertyImageId(5L).sortOrder(2).url("https://s3/a").build()));

		mockMvc.perform(multipart("/property/20/images")
				.file(new MockMultipartFile("images", "a.png", "image/png", new byte[]{1}))
				.file(new MockMultipartFile("images", "b.png", "image/png", new byte[]{2})))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$[0].propertyImageId").value(5))
			.andExpect(jsonPath("$[0].sortOrder").value(2))
			.andExpect(jsonPath("$[0].url").value("https://s3/a"));
		assertEquals(2, images.getValue().size());
	}

	@Test
	@DisplayName("사진 파트가 없어도 서비스에 넘겨 사진 오류로 판단하게 한다")
	void passesMissingImagesToService() throws Exception {
		when(propertyService.createPropertyImages(1L, 20L, null)).thenThrow(PropertyException.invalidImage());

		mockMvc.perform(multipart("/property/20/images"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
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

	@Test
	@DisplayName("매물 상세는 로그인 중개사·매물 기준으로 조회하고 신청 조건과 사진을 반환한다")
	void readsPropertyDetail() throws Exception {
		when(propertyService.readPropertyDetail(1L, 20L)).thenReturn(PropertyDto.DetailResponse.builder()
			.propertyId(20L).landlordName("박임대").leaseType(LeaseType.MONTHLY).roadAddress("서울특별시 마포구 월드컵로 61")
			.images(List.of(PropertyDto.ImageResponse.builder().propertyImageId(1L).sortOrder(0).url("https://s3/a").build()))
			.build());

		mockMvc.perform(get("/property/20/details"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.propertyId").value(20))
			.andExpect(jsonPath("$.landlordName").value("박임대"))
			.andExpect(jsonPath("$.leaseType").value("MONTHLY"))
			.andExpect(jsonPath("$.images[0].propertyImageId").value(1))
			.andExpect(jsonPath("$.images[0].url").value("https://s3/a"));
	}

	@Test
	@DisplayName("매물 상세 저장은 로그인 중개사·매물 기준으로 요청값을 넘기고 저장 결과를 반환한다")
	void savesPropertyDetail() throws Exception {
		var request = ArgumentCaptor.forClass(PropertyDto.DetailRequest.class);
		when(propertyService.updatePropertyDetail(eq(1L), eq(20L), request.capture())).thenReturn(
			PropertyDto.DetailSaveResponse.builder().propertyId(20L).propertyStatus(PropertyStatus.REQUESTED).build());

		mockMvc.perform(put("/property/20/details").contentType(MediaType.APPLICATION_JSON).content("""
				{"propertyType":"VILLA","areaPyeong":17.32,"roomCount":3,"floor":-1,"totalFloor":5,
				 "householdCount":19,"parkingCount":0,"nearbySubwayStation":"대방역"}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.propertyId").value(20))
			.andExpect(jsonPath("$.propertyStatus").value("REQUESTED"));
		assertEquals(PropertyType.VILLA, request.getValue().getPropertyType());
		assertEquals(-1, request.getValue().getFloor());
		assertEquals(5, request.getValue().getTotalFloor());
	}

	@Test
	@DisplayName("매물 상세 저장은 모든 칸이 비어 있어도 받는다 (임시 저장)")
	void savesEmptyPropertyDetail() throws Exception {
		when(propertyService.updatePropertyDetail(eq(1L), eq(20L), any(PropertyDto.DetailRequest.class))).thenReturn(
			PropertyDto.DetailSaveResponse.builder().propertyId(20L).propertyStatus(PropertyStatus.REQUESTED).build());

		mockMvc.perform(put("/property/20/details").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{\"propertyType\":\"ONE_ROOM\"}",
		"{\"areaPyeong\":0}",
		"{\"areaPyeong\":10.123}",
		"{\"roomCount\":0}",
		"{\"totalFloor\":0}",
		"{\"householdCount\":0}",
		"{\"parkingCount\":-1}"
	})
	@DisplayName("없는 매물 종류·범위를 벗어난 숫자는 서비스 호출 전에 400으로 거절한다")
	void rejectsInvalidPropertyDetail(String body) throws Exception {
		mockMvc.perform(put("/property/20/details").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		verifyNoInteractions(propertyService);
	}

	@Test
	@DisplayName("주변 지하철역이 100자를 넘으면 서비스 호출 전에 400으로 거절한다")
	void rejectsTooLongSubwayStation() throws Exception {
		mockMvc.perform(put("/property/20/details").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nearbySubwayStation\":\"" + "역".repeat(101) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		verifyNoInteractions(propertyService);
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
