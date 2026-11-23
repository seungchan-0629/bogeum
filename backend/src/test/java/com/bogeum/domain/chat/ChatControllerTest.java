package com.bogeum.domain.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.multipart.MultipartFile;

import com.bogeum.common.enums.ChatReportReason;
import com.bogeum.common.enums.MessageType;
import com.bogeum.common.enums.Role;
import com.bogeum.common.exception.ImageException;
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

	@Test
	@DisplayName("메시지를 보내면 로그인한 사용자·방 번호·내용으로 저장한 메시지를 200으로 준다")
	void createsMessage() throws Exception {
		when(chatService.createMessage(2L, 126L, "주차 되나요?")).thenReturn(ChatDto.MessageItemResponse.builder()
			.chatMessageId(712L).senderId(2L).isMine(true).messageType(MessageType.TEXT).content("주차 되나요?").build());

		mockMvc.perform(post("/chat/rooms/126/messages").contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"주차 되나요?\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.chatMessageId").value(712))
			.andExpect(jsonPath("$.isMine").value(true));
	}

	@Test
	@DisplayName("빈 글이나 2,000자를 넘는 글은 400이고 서비스를 부르지 않는다")
	void rejectsInvalidContent() throws Exception {
		for (String content : new String[] {"", "   ", "가".repeat(2001)}) {
			mockMvc.perform(post("/chat/rooms/126/messages").contentType(MediaType.APPLICATION_JSON)
					.content("{\"content\":\"" + content + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}

		verifyNoInteractions(chatService);
	}

	@Test
	@DisplayName("사진을 보내면 image 파일로 받아 저장한 사진 메시지를 200으로 준다 (CHAT_004)")
	void createsImageMessage() throws Exception {
		var image = new MockMultipartFile("image", "defect.png", "image/png", new byte[] {9, 9});
		when(chatService.createImageMessage(eq(2L), eq(126L), any())).thenReturn(ChatDto.MessageItemResponse.builder()
			.chatMessageId(713L).senderId(2L).isMine(true).messageType(MessageType.IMAGE)
			.imageUrl("https://s3.example.com/chat-images/126/a.jpg").build());

		mockMvc.perform(multipart("/chat/rooms/126/images").file(image))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.messageType").value("IMAGE"))
			.andExpect(jsonPath("$.imageUrl").value("https://s3.example.com/chat-images/126/a.jpg"));

		var sent = ArgumentCaptor.forClass(MultipartFile.class);
		verify(chatService).createImageMessage(eq(2L), eq(126L), sent.capture());
		assertEquals("defect.png", sent.getValue().getOriginalFilename());
	}

	@Test
	@DisplayName("사진이 없거나 잘못되면 400, 저장소를 쓸 수 없으면 503이다")
	void mapsImageErrors() throws Exception {
		when(chatService.createImageMessage(eq(2L), eq(126L), isNull())).thenThrow(ImageException.invalidImage());
		when(chatService.createImageMessage(eq(2L), eq(127L), any())).thenThrow(ChatException.storageUnavailable());

		mockMvc.perform(multipart("/chat/rooms/126/images"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
		mockMvc.perform(multipart("/chat/rooms/127/images").file(new MockMultipartFile("image", "a.png", "image/png", new byte[] {1})))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("CHAT_STORAGE_UNAVAILABLE"));
	}

	@Test
	@DisplayName("읽기 전용 방에 보내면 409다")
	void rejectsReadOnlyRoom() throws Exception {
		when(chatService.createMessage(2L, 126L, "안녕하세요")).thenThrow(ChatException.chatRoomReadOnly());

		mockMvc.perform(post("/chat/rooms/126/messages").contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"안녕하세요\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CHAT_ROOM_READ_ONLY"));
	}

	@Test
	@DisplayName("채팅방 목록은 로그인한 사용자의 방을 준다")
	void readsRooms() throws Exception {
		when(chatService.readRooms(2L)).thenReturn(new ChatDto.RoomListResponse(List.of(
			ChatDto.RoomItemResponse.builder().chatRoomId(126L).partnerName("김중개").unreadCount(2L).build())));

		mockMvc.perform(get("/chat/rooms"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rooms[0].chatRoomId").value(126))
			.andExpect(jsonPath("$.rooms[0].partnerName").value("김중개"))
			.andExpect(jsonPath("$.rooms[0].unreadCount").value(2));
	}

	@Test
	@DisplayName("대화 내역은 cursor가 없으면 비워서, 있으면 그대로 넘긴다")
	void readsMessages() throws Exception {
		var response = new ChatDto.MessageListResponse(List.of(ChatDto.MessageItemResponse.builder().chatMessageId(31L)
			.isMine(true).messageType(MessageType.TEXT).content("안녕하세요").build()), 31L, true, 30L);
		when(chatService.readMessages(2L, 126L, null)).thenReturn(response);
		when(chatService.readMessages(2L, 126L, 31L)).thenReturn(new ChatDto.MessageListResponse(List.of(), null, false, 30L));

		mockMvc.perform(get("/chat/rooms/126/messages"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.messages[0].content").value("안녕하세요"))
			.andExpect(jsonPath("$.messages[0].isMine").value(true))
			.andExpect(jsonPath("$.nextCursor").value(31))
			.andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.partnerLastReadMessageId").value(30));
		mockMvc.perform(get("/chat/rooms/126/messages").param("cursor", "31"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	@DisplayName("참여하지 않은 방의 대화는 404다")
	void rejectsOthersRoom() throws Exception {
		when(chatService.readMessages(2L, 999L, null)).thenThrow(ChatException.chatRoomNotFound());

		mockMvc.perform(get("/chat/rooms/999/messages"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CHAT_ROOM_NOT_FOUND"));
	}

	@Test
	@DisplayName("읽음 처리는 로그인한 사용자와 방 번호로 하고 본문 없이 204다")
	void updatesRead() throws Exception {
		mockMvc.perform(patch("/chat/rooms/126/read"))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		verify(chatService).updateRead(2L, 126L);
	}

	@Test
	@DisplayName("참여하지 않은 방의 읽음 처리는 404다")
	void rejectsReadOfOthersRoom() throws Exception {
		doThrow(ChatException.chatRoomNotFound()).when(chatService).updateRead(2L, 999L);

		mockMvc.perform(patch("/chat/rooms/999/read"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CHAT_ROOM_NOT_FOUND"));
	}

	@Test
	@DisplayName("차단과 차단 해제는 로그인한 사용자와 방 번호로 하고 본문 없이 204다 (CHAT_010)")
	void createsAndDeletesBlock() throws Exception {
		mockMvc.perform(post("/chat/rooms/126/block"))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));
		mockMvc.perform(delete("/chat/rooms/126/block"))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		verify(chatService).createBlock(2L, 126L);
		verify(chatService).deleteBlock(2L, 126L);
	}

	@Test
	@DisplayName("참여하지 않은 방의 차단은 404, 차단된 상대에게 보내면 409다")
	void mapsBlockErrors() throws Exception {
		doThrow(ChatException.chatRoomNotFound()).when(chatService).createBlock(2L, 999L);
		when(chatService.createMessage(2L, 126L, "안녕하세요")).thenThrow(ChatException.blocked());

		mockMvc.perform(post("/chat/rooms/999/block"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CHAT_ROOM_NOT_FOUND"));
		mockMvc.perform(post("/chat/rooms/126/messages").contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"안녕하세요\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CHAT_BLOCKED"));
	}

	@Test
	@DisplayName("신고는 로그인한 사용자·방 번호·사유·내용으로 하고 본문 없이 204다 (CHAT_011)")
	void createsReport() throws Exception {
		mockMvc.perform(post("/chat/rooms/126/report").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reasonType\":\"ABUSIVE\",\"content\":\"욕설을 했어요\"}"))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		verify(chatService).createReport(2L, 126L, ChatReportReason.ABUSIVE, "욕설을 했어요");
	}

	@Test
	@DisplayName("사유가 없거나 모르는 사유, 1,000자를 넘는 내용은 400이고 서비스를 부르지 않는다")
	void rejectsInvalidReport() throws Exception {
		for (String body : new String[] {"{}", "{\"reasonType\":\"UNKNOWN\"}",
				"{\"reasonType\":\"ETC\",\"content\":\"" + "가".repeat(1001) + "\"}"}) {
			mockMvc.perform(post("/chat/rooms/126/report").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}

		verifyNoInteractions(chatService);
	}

	@Test
	@DisplayName("기타 사유에 내용이 없으면 400, 같은 방을 다시 신고하면 409다")
	void mapsReportErrors() throws Exception {
		doThrow(ChatException.reportContentRequired()).when(chatService).createReport(2L, 126L, ChatReportReason.ETC, null);
		doThrow(ChatException.alreadyReported()).when(chatService).createReport(2L, 127L, ChatReportReason.FRAUD, null);

		mockMvc.perform(post("/chat/rooms/126/report").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reasonType\":\"ETC\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CHAT_REPORT_CONTENT_REQUIRED"));
		mockMvc.perform(post("/chat/rooms/127/report").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reasonType\":\"FRAUD\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CHAT_ALREADY_REPORTED"));
	}
}
