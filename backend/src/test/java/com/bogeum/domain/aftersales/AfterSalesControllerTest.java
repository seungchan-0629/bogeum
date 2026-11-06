package com.bogeum.domain.aftersales;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.bogeum.common.enums.AsStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.aftersales.model.AfterSalesDto;
import com.bogeum.domain.aftersales.model.AfterSalesRequest;
import com.bogeum.domain.aftersales.model.AfterSalesRequestImage;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.model.User;
import com.bogeum.domain.user.model.UserDto.LoginUser;

/** 조회 인자·인증 사용자 전달·JSON 응답을 확인한다. 실제 권한은 인증 통합 테스트에서 검증한다 (AS_002). */
@WebMvcTest(AfterSalesController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AfterSalesControllerTest.PrincipalResolverConfig.class)
class AfterSalesControllerTest {
	@TestConfiguration
	static class PrincipalResolverConfig implements WebMvcConfigurer {
		@Override
		public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
			resolvers.add(new AuthenticationPrincipalArgumentResolver());
		}
	}

	private final MockMvc mvc;
	@MockitoBean
	private AfterSalesService service;

	@Autowired
	AfterSalesControllerTest(MockMvc mvc) {
		this.mvc = mvc;
	}

	@BeforeEach
	void login() {
		// 기존 웹 슬라이스 테스트와 같이 필터 대신 인증 사용자만 설정한다.
		var principal = new LoginUser(User.builder().userId(7L).email("landlord@example.com")
			.passwordHash("hash").name("임대인").role(Role.USER).build());
		SecurityContextHolder.getContext().setAuthentication(
			UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
	}

	@AfterEach
	void logout() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void readsListUsingAuthenticatedUserAndRequestedPage() throws Exception {
		when(service.readReceivedAfterSalesRequests(7L, 2)).thenReturn(AfterSalesDto.ListResponse.builder()
			.requests(List.of(AfterSalesDto.ItemResponse.from(request()))).page(2).totalPages(3).totalElements(12).build());

		mvc.perform(get("/as-requests/received").param("page", "2").param("landlordId", "999"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.totalElements").value(12))
			.andExpect(jsonPath("$.requests[0].asRequestId").value(10))
			.andExpect(jsonPath("$.requests[0].propertyId").value(3))
			.andExpect(jsonPath("$.requests[0].roadAddress").value("서울특별시 마포구 테스트로 1"))
			.andExpect(jsonPath("$.requests[0].detailAddress").value("101호"))
			.andExpect(jsonPath("$.requests[0].tenantName").value("임차인"))
			.andExpect(jsonPath("$.requests[0].content").value("누수 점검"))
			.andExpect(jsonPath("$.requests[0].asStatus").value("RECEIVED"))
			.andExpect(jsonPath("$.requests[0].createdAt").value("2026-11-06T10:00:00"))
			.andExpect(jsonPath("$..email").doesNotExist()).andExpect(jsonPath("$..phone").doesNotExist())
			.andExpect(jsonPath("$..passwordHash").doesNotExist()).andExpect(jsonPath("$..tenant").doesNotExist());
		verify(service).readReceivedAfterSalesRequests(7L, 2);
	}

	@Test
	void readsEmptyFirstPageByDefault() throws Exception {
		when(service.readReceivedAfterSalesRequests(7L, 1)).thenReturn(AfterSalesDto.ListResponse.builder()
			.requests(List.of()).page(1).totalPages(0).totalElements(0).build());

		mvc.perform(get("/as-requests/received")).andExpect(status().isOk())
			.andExpect(jsonPath("$.requests").isEmpty()).andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.totalPages").value(0)).andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void serializesDetailWithoutFileKeyOrPersonalFields() throws Exception {
		var request = request();
		var image = AfterSalesRequestImage.builder().asRequestImageId(20L).asRequest(request)
			.fileKey("private/as/photo.jpg").createdAt(request.getCreatedAt()).build();
		when(service.readAfterSalesRequest(7L, 10L))
			.thenReturn(AfterSalesDto.DetailResponse.from(request, List.of(image)));

		mvc.perform(get("/as-requests/10").param("landlordId", "999")).andExpect(status().isOk())
			.andExpect(jsonPath("$.asRequestId").value(10)).andExpect(jsonPath("$.propertyId").value(3))
			.andExpect(jsonPath("$.roadAddress").value("서울특별시 마포구 테스트로 1"))
			.andExpect(jsonPath("$.detailAddress").value("101호")).andExpect(jsonPath("$.tenantName").value("임차인"))
			.andExpect(jsonPath("$.content").value("누수 점검")).andExpect(jsonPath("$.asStatus").value("RECEIVED"))
			.andExpect(jsonPath("$.createdAt").value("2026-11-06T10:00:00"))
			.andExpect(jsonPath("$.updatedAt").value("2026-11-06T10:00:00"))
			.andExpect(jsonPath("$.images[0].asRequestImageId").value(20))
			.andExpect(jsonPath("$.images[0].createdAt").value("2026-11-06T10:00:00"))
			.andExpect(jsonPath("$..fileKey").doesNotExist()).andExpect(jsonPath("$..url").doesNotExist())
			.andExpect(jsonPath("$..email").doesNotExist()).andExpect(jsonPath("$..phone").doesNotExist())
			.andExpect(jsonPath("$..passwordHash").doesNotExist()).andExpect(jsonPath("$.tenant").doesNotExist())
			.andExpect(jsonPath("$.property").doesNotExist());
		verify(service).readAfterSalesRequest(7L, 10L);
	}

	@Test
	void mapsUnavailableDetailToCommonNotFound() throws Exception {
		when(service.readAfterSalesRequest(7L, 10L)).thenThrow(AfterSalesException.requestNotFound());

		mvc.perform(get("/as-requests/10")).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("AS_REQUEST_NOT_FOUND"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"/as-requests/received?page=abc", "/as-requests/received?page=2147483648", "/as-requests/abc"})
	void rejectsInvalidReadArguments(String path) throws Exception {
		mvc.perform(get(path)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		verifyNoInteractions(service);
	}

	private AfterSalesRequest request() {
		var createdAt = LocalDateTime.of(2026, 11, 6, 10, 0);
		return AfterSalesRequest.builder().asRequestId(10L)
			.property(Property.builder().propertyId(3L).roadAddress("서울특별시 마포구 테스트로 1").detailAddress("101호").build())
			.tenant(User.builder().userId(8L).name("임차인").email("private@example.com")
				.phone("01012345678").passwordHash("private-hash").build())
			.content("누수 점검").createdAt(createdAt).updatedAt(createdAt).build();
	}
}
