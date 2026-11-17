package com.bogeum.domain.recommend;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.property.PropertyException;
import com.bogeum.domain.property.model.PropertyDto;
import com.bogeum.domain.recommend.model.RecommendDto;
import com.bogeum.domain.recommend.model.SimilarBasis;
import com.bogeum.domain.user.model.User;
import com.bogeum.domain.user.model.UserDto.LoginUser;

/** 매물 추천 요청과 응답 형식을 DB 없이 확인한다. 권한 검사는 Security 설정이 맡는다. */
@WebMvcTest(RecommendController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RecommendControllerTest.PrincipalResolverConfig.class)
class RecommendControllerTest {

	// 웹 슬라이스 테스트에는 @AuthenticationPrincipal 처리기가 없어 직접 등록한다.
	@TestConfiguration
	static class PrincipalResolverConfig implements WebMvcConfigurer {
		@Override
		public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
			resolvers.add(new AuthenticationPrincipalArgumentResolver());
		}
	}

	private final MockMvc mockMvc;

	@MockitoBean
	private RecommendService recommendService;

	@Autowired
	RecommendControllerTest(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	// 필터를 끈 테스트라 JWT 필터 대신 로그인 사용자를 직접 넣는다.
	@BeforeEach
	void login() {
		var principal = new LoginUser(User.builder().userId(1L).email("user@example.com").passwordHash("hash")
			.name("김임차").role(Role.USER).build());
		SecurityContextHolder.getContext().setAuthentication(
			UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
	}

	@AfterEach
	void logout() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("추천 매물은 로그인 사용자 기준으로 조회하고 카드와 추천에 쓴 관심 지역·역을 반환한다")
	void readsRecommendations() throws Exception {
		when(recommendService.readRecommendations(1L, 6)).thenReturn(RecommendDto.RecommendListResponse.builder()
			.properties(List.of(PropertyDto.PropertyItemResponse.builder().propertyId(20L).leaseType(LeaseType.MONTHLY)
				.deposit(BigDecimal.valueOf(10_000_000)).monthlyRent(BigDecimal.valueOf(700_000))
				.roadAddress("서울특별시 마포구 월드컵로 61").nearbySubwayStation("합정역").build()))
			.regions(List.of("서울특별시 마포구")).stations(List.of("합정역")).isPersonalized(true).build());

		mockMvc.perform(get("/recommend/properties").param("size", "6"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.properties[0].propertyId").value(20))
			.andExpect(jsonPath("$.regions[0]").value("서울특별시 마포구"))
			.andExpect(jsonPath("$.stations[0]").value("합정역"))
			.andExpect(jsonPath("$.isPersonalized").value(true));
	}

	@Test
	@DisplayName("개수를 보내지 않으면 메인 화면에 맞춰 3개를 조회한다")
	void readsThreeByDefault() throws Exception {
		when(recommendService.readRecommendations(1L, 3)).thenReturn(RecommendDto.RecommendListResponse.builder()
			.properties(List.of()).regions(List.of()).stations(List.of()).isPersonalized(false).build());

		mockMvc.perform(get("/recommend/properties"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isPersonalized").value(false));
		verify(recommendService).readRecommendations(1L, 3);
	}

	@Test
	@DisplayName("매물 상세의 비슷한 매물은 로그인 사용자·매물 기준으로 조회하고 카드를 반환한다")
	void readsSimilarProperties() throws Exception {
		when(recommendService.readSimilarProperties(1L, 20L, 3)).thenReturn(RecommendDto.SimilarListResponse.builder()
			.properties(List.of(PropertyDto.PropertyItemResponse.builder().propertyId(21L).build())).build());

		mockMvc.perform(get("/recommend/properties/20/similar"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.properties[0].propertyId").value(21));
	}

	@Test
	@DisplayName("공개 중이 아닌 매물의 비슷한 매물은 404와 PROPERTY_NOT_FOUND를 반환한다")
	void rejectsSimilarOfNotPublished() throws Exception {
		when(recommendService.readSimilarProperties(1L, 999L, 3)).thenThrow(PropertyException.propertyNotFound());

		mockMvc.perform(get("/recommend/properties/999/similar"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROPERTY_NOT_FOUND"));
	}

	@Test
	@DisplayName("최근 본 매물·관심 매물 기준 비슷한 매물은 basis로 기준을 받아 조회한다")
	void readsSimilarByBasis() throws Exception {
		when(recommendService.readSimilarByBasis(1L, SimilarBasis.FAVORITE, 3)).thenReturn(
			RecommendDto.SimilarListResponse.builder().properties(List.of()).build());

		mockMvc.perform(get("/recommend/similar").param("basis", "FAVORITE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.properties").isEmpty());
		verify(recommendService).readSimilarByBasis(1L, SimilarBasis.FAVORITE, 3);
	}

	@Test
	@DisplayName("basis가 없거나 VIEW·FAVORITE가 아니면 서비스 호출 전에 400으로 거절한다")
	void rejectsInvalidBasis() throws Exception {
		mockMvc.perform(get("/recommend/similar").param("basis", "SEARCH"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mockMvc.perform(get("/recommend/similar"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("개수가 숫자가 아니면 서비스 호출 전에 400으로 거절한다")
	void rejectsInvalidSize() throws Exception {
		mockMvc.perform(get("/recommend/properties").param("size", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
}
