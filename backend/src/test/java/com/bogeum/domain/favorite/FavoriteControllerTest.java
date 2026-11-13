package com.bogeum.domain.favorite;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.bogeum.common.enums.Role;
import com.bogeum.domain.property.PropertyException;
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
		verifyNoInteractions(favoriteService);
	}
}
