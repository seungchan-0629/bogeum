package com.bogeum.domain.chat;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.bogeum.common.enums.Role;
import com.bogeum.domain.chat.model.ChatDto;
import com.bogeum.domain.property.PropertyException;
import com.bogeum.domain.user.model.User;
import com.bogeum.domain.user.model.UserDto.LoginUser;

/** 채팅방 열기 요청의 주소·응답을 DB 없이 확인한다. 권한 검사는 Security 설정이 맡는다. */
@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ChatControllerTest.PrincipalResolverConfig.class)
class ChatControllerTest {

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
	private ChatService chatService;

	@Autowired
	ChatControllerTest(MockMvc mockMvc) {
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
	@DisplayName("채팅방을 열면 로그인한 사용자와 매물 번호로 열린 방 번호를 200으로 준다")
	void createsRoom() throws Exception {
		when(chatService.createRoom(2L, 20L)).thenReturn(new ChatDto.RoomResponse(126L));

		mockMvc.perform(post("/chat/create").contentType(MediaType.APPLICATION_JSON).content("{\"propertyId\":20}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.chatRoomId").value(126));
	}

	@Test
	@DisplayName("매물 번호가 없으면 400이고 서비스를 부르지 않는다")
	void rejectsMissingPropertyId() throws Exception {
		mockMvc.perform(post("/chat/create").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		verifyNoInteractions(chatService);
	}

	@Test
	@DisplayName("열 수 없는 매물은 404, 중개사가 수락하기 전이면 409다")
	void mapsServiceErrors() throws Exception {
		when(chatService.createRoom(2L, 20L)).thenThrow(PropertyException.propertyNotFound());
		when(chatService.createRoom(2L, 21L)).thenThrow(ChatException.agentNotAssigned());

		mockMvc.perform(post("/chat/create").contentType(MediaType.APPLICATION_JSON).content("{\"propertyId\":20}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROPERTY_NOT_FOUND"));
		mockMvc.perform(post("/chat/create").contentType(MediaType.APPLICATION_JSON).content("{\"propertyId\":21}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CHAT_AGENT_NOT_ASSIGNED"));
	}
}
