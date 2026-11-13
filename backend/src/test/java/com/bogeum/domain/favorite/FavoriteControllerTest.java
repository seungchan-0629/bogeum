package com.bogeum.domain.favorite;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import com.bogeum.common.enums.PropertyType;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.favorite.model.FavoriteDto;
import com.bogeum.domain.property.PropertyException;
import com.bogeum.domain.property.model.PropertyDto;
import com.bogeum.domain.user.model.User;
import com.bogeum.domain.user.model.UserDto.LoginUser;

/** 관심 매물 요청의 주소·응답을 DB 없이 확인한다. 권한 검사는 Security 설정이 맡는다. */
@WebMvcTest(FavoriteController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(FavoriteControllerTest.PrincipalResolverConfig.class)
class FavoriteControllerTest {

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
	private FavoriteService favoriteService;

	@Autowired
	FavoriteControllerTest(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	// 필터를 끈 테스트라 JWT 필터 대신 로그인 사용자를 직접 넣는다.
	@BeforeEach
	void login() {
		var principal = new LoginUser(User.builder().userId(2L).email("user@example.com").passwordHash("hash")
			.name("최세입").role(Role.USER).build());
		SecurityContextHolder.getContext().setAuthentication(
			UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
	}

	@AfterEach
	void logout() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("관심 매물 등록은 로그인 사용자 기준으로 처리하고 본문 없이 204를 반환한다")
	void createsFavorite() throws Exception {
		mockMvc.perform(put("/favorites/20"))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		verify(favoriteService).createFavorite(2L, 20L);
	}

	@Test
	@DisplayName("없거나 공개 중이 아닌 매물이면 404와 PROPERTY_NOT_FOUND를 반환한다")
	void rejectsPropertyNotPublic() throws Exception {
		doThrow(PropertyException.propertyNotFound()).when(favoriteService).createFavorite(2L, 999L);

		mockMvc.perform(put("/favorites/999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROPERTY_NOT_FOUND"));
	}

	@Test
	@DisplayName("본인이 중개를 신청한 매물이면 400과 FAVORITE_OWN_PROPERTY를 반환한다")
	void rejectsOwnProperty() throws Exception {
		doThrow(FavoriteException.ownProperty()).when(favoriteService).createFavorite(2L, 20L);

		mockMvc.perform(put("/favorites/20"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FAVORITE_OWN_PROPERTY"));
	}

	@Test
	@DisplayName("매물 번호가 숫자가 아니면 서비스 호출 전에 400으로 거절한다")
	void rejectsInvalidPropertyId() throws Exception {
		mockMvc.perform(put("/favorites/abc"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(delete("/favorites/abc"))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(favoriteService);
	}

	@Test
	@DisplayName("관심 매물 목록은 로그인 사용자 기준으로 조회하고 매물 카드·담은 시각·공개 여부·페이지 정보를 반환한다")
	void readsFavorites() throws Exception {
		when(favoriteService.readFavorites(2L, 2)).thenReturn(FavoriteDto.FavoriteListResponse.builder()
			.favorites(List.of(FavoriteDto.FavoriteItemResponse.builder()
				.property(PropertyDto.PropertyItemResponse.builder().propertyId(54L).thumbnailUrl("https://s3/54.jpg")
					.propertyType(PropertyType.OFFICETEL).leaseType(LeaseType.JEONSE).deposit(new BigDecimal("170000000"))
					.monthlyRent(BigDecimal.ZERO).areaPyeong(new BigDecimal("11.90"))
					.roadAddress("경기도 수원시 영통구 광교중앙로 43").nearbySubwayStation("광교중앙역").build())
				.favoritedAt(LocalDateTime.of(2026, 11, 13, 15, 30)).isAvailable(false).build()))
			.page(2).totalPages(2).totalElements(13L).build());

		mockMvc.perform(get("/favorites/me").param("page", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.favorites[0].property.propertyId").value(54))
			.andExpect(jsonPath("$.favorites[0].property.thumbnailUrl").value("https://s3/54.jpg"))
			.andExpect(jsonPath("$.favorites[0].property.detailAddress").doesNotExist())
			.andExpect(jsonPath("$.favorites[0].favoritedAt").value("2026-11-13T15:30:00"))
			.andExpect(jsonPath("$.favorites[0].isAvailable").value(false))
			.andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.totalElements").value(13));
	}

	@Test
	@DisplayName("관심 매물 목록 페이지를 보내지 않으면 첫 페이지를 조회한다")
	void readsFirstPageByDefault() throws Exception {
		when(favoriteService.readFavorites(2L, 1)).thenReturn(FavoriteDto.FavoriteListResponse.builder()
			.favorites(List.of()).page(1).totalPages(0).totalElements(0L).build());

		mockMvc.perform(get("/favorites/me"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.favorites").isEmpty());
	}

	@Test
	@DisplayName("담은 매물 번호는 로그인 사용자 기준으로 조회해 번호 목록을 반환한다")
	void readsFavoriteIds() throws Exception {
		when(favoriteService.readFavoriteIds(2L)).thenReturn(
			FavoriteDto.FavoriteIdsResponse.builder().propertyIds(List.of(54L, 31L)).build());

		mockMvc.perform(get("/favorites/me/ids"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.propertyIds[0]").value(54))
			.andExpect(jsonPath("$.propertyIds[1]").value(31));
	}

	@Test
	@DisplayName("관심 매물 해제는 로그인 사용자 기준으로 처리하고 본문 없이 204를 반환한다")
	void deletesFavorite() throws Exception {
		mockMvc.perform(delete("/favorites/20"))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		verify(favoriteService).deleteFavorite(2L, 20L);
	}
}
