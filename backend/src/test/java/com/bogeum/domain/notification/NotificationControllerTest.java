package com.bogeum.domain.notification;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.bogeum.common.enums.NotificationRefType;
import com.bogeum.common.enums.NotificationType;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.notification.model.NotificationDto;
import com.bogeum.domain.user.model.User;
import com.bogeum.domain.user.model.UserDto.LoginUser;

/** 알림 목록·안 읽은 수·읽음 처리 요청의 주소·응답을 DB 없이 확인한다. 로그인 여부는 Security 설정이 맡는다. */
@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(NotificationControllerTest.PrincipalResolverConfig.class)
class NotificationControllerTest {

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
	private NotificationService notificationService;

	@Autowired
	NotificationControllerTest(MockMvc mockMvc) {
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
	@DisplayName("목록은 기본 20개, 안 읽은 것 전부로 로그인한 사용자의 알림을 200으로 준다")
	void readsNotifications() throws Exception {
		var item = NotificationDto.ItemResponse.builder().notificationId(31L).type(NotificationType.CHAT_MESSAGE)
			.title("새 메시지").body("김중개: 안녕하세요").refType(NotificationRefType.CHAT_ROOM).refId(3L).isRead(false)
			.createdAt(LocalDateTime.of(2026, 11, 26, 10, 0)).build();
		when(notificationService.readNotifications(2L, null, 20, false))
			.thenReturn(new NotificationDto.ListResponse(List.of(item), 31L, true));

		mockMvc.perform(get("/notifications/me"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notifications[0].notificationId").value(31))
			.andExpect(jsonPath("$.notifications[0].type").value("CHAT_MESSAGE"))
			.andExpect(jsonPath("$.notifications[0].refType").value("CHAT_ROOM"))
			.andExpect(jsonPath("$.notifications[0].refId").value(3))
			.andExpect(jsonPath("$.notifications[0].isRead").value(false))
			.andExpect(jsonPath("$.nextCursor").value(31))
			.andExpect(jsonPath("$.hasNext").value(true));
	}

	@Test
	@DisplayName("헤더 미리보기처럼 size·cursor·unreadOnly를 보내면 그대로 넘긴다")
	void passesParameters() throws Exception {
		when(notificationService.readNotifications(2L, 40L, 5, true))
			.thenReturn(new NotificationDto.ListResponse(List.of(), null, false));

		mockMvc.perform(get("/notifications/me").param("cursor", "40").param("size", "5").param("unreadOnly", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	@DisplayName("size가 범위를 벗어나거나 숫자가 아니면 400이다")
	void rejectsInvalidSize() throws Exception {
		when(notificationService.readNotifications(2L, null, 21, false)).thenThrow(NotificationException.invalidRequest());

		mockMvc.perform(get("/notifications/me").param("size", "21"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mockMvc.perform(get("/notifications/me").param("size", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("안 읽은 알림 수를 200으로 준다")
	void readsUnreadCount() throws Exception {
		when(notificationService.readUnreadCount(2L)).thenReturn(new NotificationDto.UnreadCountResponse(4L));

		mockMvc.perform(get("/notifications/unread-count"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unreadCount").value(4));
	}

	@Test
	@DisplayName("알림 하나 읽음과 모두 읽음은 본문 없이 204다")
	void updatesRead() throws Exception {
		mockMvc.perform(patch("/notifications/31/read"))
			.andExpect(status().isNoContent());
		mockMvc.perform(patch("/notifications/read-all"))
			.andExpect(status().isNoContent());

		verify(notificationService).updateRead(2L, 31L);
		verify(notificationService).updateReadAll(2L);
	}

	@Test
	@DisplayName("내 알림이 아니면 404, 번호가 숫자가 아니면 400이다")
	void mapsReadErrors() throws Exception {
		doThrow(NotificationException.notificationNotFound()).when(notificationService).updateRead(2L, 99L);

		mockMvc.perform(patch("/notifications/99/read"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
		mockMvc.perform(patch("/notifications/abc/read"))
			.andExpect(status().isBadRequest());
	}
}
