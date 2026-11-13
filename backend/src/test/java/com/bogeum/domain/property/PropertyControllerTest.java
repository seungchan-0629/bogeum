package com.bogeum.domain.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
	@DisplayName("매물 목록 첫 화면은 커서 없이 조회하고 카드 정보와 다음 커서를 반환한다")
	void readsFirstProperties() throws Exception {
		when(propertyService.readProperties(any(PropertyDto.SearchRequest.class), isNull(), isNull()))
			.thenReturn(PropertyDto.PropertyListResponse.builder()
			.properties(List.of(PropertyDto.PropertyItemResponse.builder().propertyId(31L)
				.thumbnailUrl("https://s3/property-images/31/a.jpg").propertyType(PropertyType.OFFICETEL)
				.leaseType(LeaseType.MONTHLY).deposit(new BigDecimal("10000000")).monthlyRent(new BigDecimal("700000"))
				.areaPyeong(new BigDecimal("8.50")).roadAddress("서울특별시 마포구 월드컵로 61")
				.nearbySubwayStation("월드컵경기장역").build(),
				PropertyDto.PropertyItemResponse.builder().propertyId(30L).propertyType(PropertyType.VILLA)
				.leaseType(LeaseType.JEONSE).deposit(new BigDecimal("300000000")).monthlyRent(BigDecimal.ZERO)
				.areaPyeong(new BigDecimal("15.00")).roadAddress("서울특별시 동작구 노량진로 81").build()))
			.hasNext(true)
			.nextCursor(PropertyDto.PropertyCursor.builder()
				.cursorPublishedAt(LocalDateTime.of(2026, 11, 12, 10, 0, 30, 123456000)).cursorPropertyId(31L).build())
			.build());

		mockMvc.perform(get("/property"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.properties[0].propertyId").value(31))
			.andExpect(jsonPath("$.properties[0].thumbnailUrl").value("https://s3/property-images/31/a.jpg"))
			.andExpect(jsonPath("$.properties[0].deposit").value(10000000))
			.andExpect(jsonPath("$.properties[0].areaPyeong").value(8.5))
			.andExpect(jsonPath("$.properties[0].detailAddress").doesNotExist())
			.andExpect(jsonPath("$.properties[0].nearbySubwayStation").value("월드컵경기장역"))
			// 지하철역이 없는 매물은 빈 값으로 줘서 화면이 "정보 없음"을 보여 준다
			.andExpect(jsonPath("$.properties[1].nearbySubwayStation").isEmpty())
			.andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.nextCursor.cursorPublishedAt").value("2026-11-12T10:00:30.123456"))
			.andExpect(jsonPath("$.nextCursor.cursorPropertyId").value(31));
	}

	@Test
	@DisplayName("응답의 다음 커서를 그대로 보내면 공개 시각과 매물 번호로 바꿔 서비스에 넘긴다")
	void readsPropertiesAfterCursor() throws Exception {
		when(propertyService.readProperties(any(PropertyDto.SearchRequest.class),
				eq(LocalDateTime.of(2026, 11, 12, 10, 0, 30, 123456000)), eq(31L)))
			.thenReturn(PropertyDto.PropertyListResponse.builder().properties(List.of()).hasNext(false).build());

		mockMvc.perform(get("/property")
				.param("cursorPublishedAt", "2026-11-12T10:00:30.123456").param("cursorPropertyId", "31"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hasNext").value(false))
			.andExpect(jsonPath("$.nextCursor").isEmpty());
	}

	@Test
	@DisplayName("검색 조건을 쿼리로 보내면 여러 개 고르기·범위·글자 조건을 그대로 받아 서비스에 넘긴다")
	void readsPropertiesWithSearch() throws Exception {
		var search = ArgumentCaptor.forClass(PropertyDto.SearchRequest.class);
		when(propertyService.readProperties(search.capture(), isNull(), isNull()))
			.thenReturn(PropertyDto.PropertyListResponse.builder().properties(List.of()).hasNext(false).build());

		mockMvc.perform(get("/property")
				.param("propertyTypes", "APARTMENT", "VILLA").param("leaseTypes", "JEONSE,MONTHLY")
				.param("minDeposit", "10000000").param("maxDeposit", "300000000")
				.param("minMonthlyRent", "0").param("maxMonthlyRent", "800000")
				.param("minArea", "10.5").param("minHouseholdCount", "100").param("minParkingCount", "1")
				.param("minFloor", "-1").param("minRoomCount", "2")
				.param("region", "서울 강남구").param("subway", "역삼"))
			.andExpect(status().isOk());

		var value = search.getValue();
		assertEquals(List.of(PropertyType.APARTMENT, PropertyType.VILLA), value.getPropertyTypes());
		assertEquals(List.of(LeaseType.JEONSE, LeaseType.MONTHLY), value.getLeaseTypes());
		assertEquals(new BigDecimal("10000000"), value.getMinDeposit());
		assertEquals(new BigDecimal("300000000"), value.getMaxDeposit());
		assertEquals(BigDecimal.ZERO, value.getMinMonthlyRent());
		assertEquals(new BigDecimal("800000"), value.getMaxMonthlyRent());
		assertEquals(new BigDecimal("10.5"), value.getMinArea());
		assertEquals(100, value.getMinHouseholdCount());
		assertEquals(1, value.getMinParkingCount());
		assertEquals(-1, value.getMinFloor());
		assertEquals(2, value.getMinRoomCount());
		assertEquals("서울 강남구", value.getRegion());
		assertEquals("역삼", value.getSubway());
	}

	@ParameterizedTest
	@ValueSource(strings = {"leaseTypes=SALE", "propertyTypes=ONE_ROOM", "minDeposit=-1", "maxMonthlyRent=-1",
		"minArea=-1", "minArea=1.234", "minRoomCount=-1", "minParkingCount=abc",
		"minDeposit=300000000&maxDeposit=100000000", "minMonthlyRent=900000&maxMonthlyRent=500000"})
	@DisplayName("없는 선택지, 음수, 최소가 최대보다 큰 범위는 서비스 호출 전에 400으로 거절한다")
	void rejectsInvalidSearch(String query) throws Exception {
		mockMvc.perform(get("/property?" + query))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		verifyNoInteractions(propertyService);
	}

	@Test
	@DisplayName("지운 검색어(keyword) 쿼리가 와도 오류 없이 무시하고 목록을 조회한다")
	void ignoresRemovedKeyword() throws Exception {
		var search = ArgumentCaptor.forClass(PropertyDto.SearchRequest.class);
		when(propertyService.readProperties(search.capture(), isNull(), isNull()))
			.thenReturn(PropertyDto.PropertyListResponse.builder().properties(List.of()).hasNext(false).build());

		mockMvc.perform(get("/property").param("keyword", "마포 공덕역"))
			.andExpect(status().isOk());

		assertEquals(null, search.getValue().getRegion());
		assertEquals(null, search.getValue().getSubway());
	}

	@ParameterizedTest
	@ValueSource(strings = {"region", "subway"})
	@DisplayName("지역·지하철역이 50자를 넘으면 서비스 호출 전에 400으로 거절한다")
	void rejectsTooLongText(String name) throws Exception {
		mockMvc.perform(get("/property").param(name, "가".repeat(51)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		verifyNoInteractions(propertyService);
	}

	@Test
	@DisplayName("커서 형식이 틀리면 서비스 호출 전에 400으로 거절한다")
	void rejectsInvalidCursorFormat() throws Exception {
		mockMvc.perform(get("/property").param("cursorPublishedAt", "어제").param("cursorPropertyId", "31"))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(propertyService);
	}

	@Test
	@DisplayName("매물 상세는 상세 항목·사진·담당 중개사를 반환하고 임대인 이름·상세주소·공개 상태는 주지 않는다")
	void readsPublicProperty() throws Exception {
		when(propertyService.readPublicProperty(20L)).thenReturn(PropertyDto.PublicDetailResponse.builder()
			.propertyId(20L).propertyType(PropertyType.VILLA).leaseType(LeaseType.JEONSE)
			.deposit(new BigDecimal("300000000")).monthlyRent(BigDecimal.ZERO).areaPyeong(new BigDecimal("17.32"))
			.householdCount(19).parkingCount(0).floor(-1).totalFloor(5).roomCount(3)
			.roadAddress("서울특별시 영등포구 여의대방로21길 8").nearbySubwayStation("대방역")
			.images(List.of(PropertyDto.PublicImageResponse.builder().sortOrder(0).url("https://s3/a.jpg").build()))
			.agent(PropertyDto.AgentInfoResponse.builder().agentName("김중개").officeName("대방 공인중개사사무소").build())
			.build());

		mockMvc.perform(get("/property/20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.propertyId").value(20))
			.andExpect(jsonPath("$.deposit").value(300000000))
			.andExpect(jsonPath("$.floor").value(-1))
			.andExpect(jsonPath("$.parkingCount").value(0))
			.andExpect(jsonPath("$.images[0].url").value("https://s3/a.jpg"))
			.andExpect(jsonPath("$.images[0].propertyImageId").doesNotExist())
			.andExpect(jsonPath("$.agent.agentName").value("김중개"))
			.andExpect(jsonPath("$.agent.officeName").value("대방 공인중개사사무소"))
			.andExpect(jsonPath("$.landlordName").doesNotExist())
			.andExpect(jsonPath("$.detailAddress").doesNotExist())
			.andExpect(jsonPath("$.propertyStatus").doesNotExist());
	}

	@Test
	@DisplayName("없거나 공개 중이 아닌 매물 상세는 404와 PROPERTY_NOT_FOUND를 반환한다")
	void rejectsPropertyNotPublic() throws Exception {
		when(propertyService.readPublicProperty(999L)).thenThrow(PropertyException.propertyNotFound());

		mockMvc.perform(get("/property/999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROPERTY_NOT_FOUND"));
	}

	@Test
	@DisplayName("내 매물 목록은 로그인 사용자 기준으로 조회하고 진행 상태·담당 중개사·상세주소·페이지 정보를 반환한다")
	void readsMyProperties() throws Exception {
		when(propertyService.readMyProperties(1L, 2)).thenReturn(PropertyDto.MyPropertyListResponse.builder()
			.properties(List.of(PropertyDto.MyPropertyItemResponse.builder().propertyId(20L).brokerageRequestId(10L)
				.roadAddress("서울특별시 마포구 월드컵로 61").detailAddress("302호").leaseType(LeaseType.MONTHLY)
				.deposit(new BigDecimal("10000000")).monthlyRent(new BigDecimal("700000"))
				.brokerageStatus(BrokerageStatus.ACCEPTED).propertyStatus(PropertyStatus.HIDDEN)
				.agentName("김중개").officeName("망원 행복공인중개사사무소").isDesignated(false)
				.requestedAt(LocalDateTime.of(2026, 11, 13, 10, 0)).build()))
			.page(2).totalPages(2).totalElements(11L).build());

		mockMvc.perform(get("/property/mine").param("page", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.properties[0].propertyId").value(20))
			.andExpect(jsonPath("$.properties[0].detailAddress").value("302호"))
			.andExpect(jsonPath("$.properties[0].brokerageStatus").value("ACCEPTED"))
			.andExpect(jsonPath("$.properties[0].propertyStatus").value("HIDDEN"))
			.andExpect(jsonPath("$.properties[0].agentName").value("김중개"))
			.andExpect(jsonPath("$.properties[0].officeName").value("망원 행복공인중개사사무소"))
			.andExpect(jsonPath("$.properties[0].isDesignated").value(false))
			.andExpect(jsonPath("$.properties[0].requestedAt").value("2026-11-13T10:00:00"))
			.andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.totalElements").value(11));
	}

	@Test
	@DisplayName("내 매물 목록 페이지를 보내지 않으면 첫 페이지를 조회하고, 상세 조회(/{propertyId})와 섞이지 않는다")
	void readsFirstPageOfMyProperties() throws Exception {
		when(propertyService.readMyProperties(1L, 1)).thenReturn(PropertyDto.MyPropertyListResponse.builder()
			.properties(List.of()).page(1).totalPages(0).totalElements(0L).build());

		mockMvc.perform(get("/property/mine"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.properties").isEmpty());
		verify(propertyService, never()).readPublicProperty(any());
	}

	@Test
	@DisplayName("내 매물 수정 화면 첫 값은 로그인 사용자 기준으로 조회하고 수정 가능 여부를 isUpdatable로 준다")
	void readsMyProperty() throws Exception {
		when(propertyService.readMyProperty(1L, 20L)).thenReturn(PropertyDto.MyPropertyDetailResponse.builder()
			.propertyId(20L).leaseType(LeaseType.MONTHLY).deposit(BigDecimal.valueOf(10_000_000))
			.monthlyRent(BigDecimal.valueOf(700_000)).roadAddress("서울특별시 마포구 월드컵로 61").detailAddress("302호")
			.brokerageStatus(BrokerageStatus.ACCEPTED).propertyStatus(PropertyStatus.ACTIVE).isUpdatable(true).build());

		mockMvc.perform(get("/property/mine/20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.propertyId").value(20))
			.andExpect(jsonPath("$.leaseType").value("MONTHLY"))
			.andExpect(jsonPath("$.detailAddress").value("302호"))
			.andExpect(jsonPath("$.isUpdatable").value(true));
	}

	@Test
	@DisplayName("내 매물 수정은 로그인 사용자·매물 기준으로 처리하고 204를 반환한다")
	void updatesMyProperty() throws Exception {
		mockMvc.perform(put("/property/mine/20").contentType(MediaType.APPLICATION_JSON)
				.content("{\"leaseType\":\"JEONSE\",\"deposit\":300000000}"))
			.andExpect(status().isNoContent());

		ArgumentCaptor<PropertyDto.LeaseTermsRequest> captor = ArgumentCaptor.forClass(PropertyDto.LeaseTermsRequest.class);
		verify(propertyService).updateMyProperty(eq(1L), eq(20L), captor.capture());
		assertEquals(LeaseType.JEONSE, captor.getValue().getLeaseType());
		assertEquals(0, BigDecimal.valueOf(300_000_000).compareTo(captor.getValue().getDeposit()));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{\"deposit\":10000000}",
		"{\"leaseType\":\"MONTHLY\"}",
		"{\"leaseType\":\"MONTHLY\",\"deposit\":-1,\"monthlyRent\":700000}",
		"{\"leaseType\":\"MONTHLY\",\"deposit\":10000000,\"monthlyRent\":-1}",
		"{\"leaseType\":\"MONTHLY\",\"deposit\":1234567890123456,\"monthlyRent\":700000}"
	})
	@DisplayName("내 매물 수정 값이 없거나 음수, 15자리를 넘으면 서비스 호출 전에 400으로 거절한다")
	void rejectsInvalidLeaseTerms(String body) throws Exception {
		mockMvc.perform(put("/property/mine/20").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		verifyNoInteractions(propertyService);
	}

	@Test
	@DisplayName("계약이 끝난 매물을 수정하면 409와 PROPERTY_NOT_UPDATABLE을 반환한다")
	void rejectsUpdatingContractedProperty() throws Exception {
		doThrow(PropertyException.notUpdatable()).when(propertyService)
			.updateMyProperty(eq(1L), eq(20L), any(PropertyDto.LeaseTermsRequest.class));

		mockMvc.perform(put("/property/mine/20").contentType(MediaType.APPLICATION_JSON)
				.content("{\"leaseType\":\"MONTHLY\",\"deposit\":10000000,\"monthlyRent\":700000}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PROPERTY_NOT_UPDATABLE"));
	}

	@Test
	@DisplayName("매물 번호가 숫자가 아니면 서비스 호출 전에 400으로 거절한다")
	void rejectsInvalidPropertyId() throws Exception {
		mockMvc.perform(get("/property/abc"))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(propertyService);
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

	@Test
	@DisplayName("매물 공개는 로그인 중개사·매물 기준으로 처리하고 공개 상태를 반환한다")
	void publishesProperty() throws Exception {
		when(propertyService.publishProperty(1L, 20L)).thenReturn(
			PropertyDto.DetailSaveResponse.builder().propertyId(20L).propertyStatus(PropertyStatus.ACTIVE).build());

		mockMvc.perform(post("/property/20/publish"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.propertyId").value(20))
			.andExpect(jsonPath("$.propertyStatus").value("ACTIVE"));
	}

	@Test
	@DisplayName("필수 정보나 사진이 없으면 400과 PROPERTY_NOT_PUBLISHABLE을 반환한다")
	void rejectsPublishingIncompleteProperty() throws Exception {
		when(propertyService.publishProperty(1L, 20L)).thenThrow(PropertyException.notPublishable());

		mockMvc.perform(post("/property/20/publish"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PROPERTY_NOT_PUBLISHABLE"));
	}

	@Test
	@DisplayName("사진 삭제는 로그인 중개사·매물·사진 기준으로 처리하고 204를 반환한다")
	void deletesPropertyImage() throws Exception {
		mockMvc.perform(delete("/property/20/images/5"))
			.andExpect(status().isNoContent());
		verify(propertyService).deletePropertyImage(1L, 20L, 5L);
	}

	@Test
	@DisplayName("그 매물의 사진이 아니면 404와 PROPERTY_IMAGE_NOT_FOUND를 반환한다")
	void rejectsDeletingMissingImage() throws Exception {
		doThrow(PropertyException.propertyImageNotFound()).when(propertyService).deletePropertyImage(1L, 20L, 77L);

		mockMvc.perform(delete("/property/20/images/77"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROPERTY_IMAGE_NOT_FOUND"));
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
