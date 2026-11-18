package com.bogeum.domain.chat;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bogeum.domain.chat.model.ChatDto;
import com.bogeum.domain.user.model.UserDto.LoginUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequestMapping("/chat")
@RestController
@RequiredArgsConstructor
public class ChatController {
	private final ChatService chatService;

	// 매물로 대화할 채팅방을 열고 번호를 준다. 새로 만든 방이든 기존 방이든 200이다 (CHAT_002)
	// 임차인은 문의 방, 임대인(본인 매물)과 중개사(맡은 매물)는 임대인–중개사 방이 열린다. 방 종류는 서비스가 고른다
	@PostMapping("/create")
	public ChatDto.RoomResponse createRoom(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody ChatDto.RoomRequest request) {
		return chatService.createRoom(loginUser.getUserId(), request.getPropertyId());
	}

	// 내가 참여한 채팅방 전부. 최근 대화 순이고 방마다 안 읽은 메시지 수를 준다 (CHAT_005, CHAT_007)
	@GetMapping("/rooms")
	public ChatDto.RoomListResponse readRooms(@AuthenticationPrincipal LoginUser loginUser) {
		return chatService.readRooms(loginUser.getUserId());
	}

	// 채팅방의 이전 대화. cursor가 없으면 최근 30개, 있으면 그 메시지보다 오래된 30개다 (CHAT_006)
	// 참여하지 않은 방이면 CHAT_ROOM_NOT_FOUND
	@GetMapping("/rooms/{chatRoomId}/messages")
	public ChatDto.MessageListResponse readMessages(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable("chatRoomId") Long chatRoomId, @RequestParam(value = "cursor", required = false) Long cursor) {
		return chatService.readMessages(loginUser.getUserId(), chatRoomId, cursor);
	}
}
