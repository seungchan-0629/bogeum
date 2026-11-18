package com.bogeum.domain.chat;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
