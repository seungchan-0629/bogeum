package com.bogeum.domain.chat;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

	// 채팅방에 글을 보낸다. 저장한 메시지를 돌려주고, 방을 보고 있는 사람에게는 STOMP로 바로 전달한다 (CHAT_003)
	// 참여하지 않은 방은 CHAT_ROOM_NOT_FOUND, 읽기 전용 방은 CHAT_ROOM_READ_ONLY
	@PostMapping("/rooms/{chatRoomId}/messages")
	public ChatDto.MessageItemResponse createMessage(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable("chatRoomId") Long chatRoomId, @Valid @RequestBody ChatDto.MessageRequest request) {
		return chatService.createMessage(loginUser.getUserId(), chatRoomId, request.getContent());
	}

	// 채팅방에 사진 한 장을 보낸다. 글 보내기와 같이 저장한 메시지(imageUrl 포함)를 돌려주고 방 구독자에게 바로 전달한다 (CHAT_004)
	// 사진이 없거나 형식·용량을 벗어나면 INVALID_IMAGE, 저장소를 쓸 수 없으면 CHAT_STORAGE_UNAVAILABLE
	@PostMapping(value = "/rooms/{chatRoomId}/images", consumes = "multipart/form-data")
	public ChatDto.MessageItemResponse createImageMessage(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable("chatRoomId") Long chatRoomId, @RequestPart(value = "image", required = false) MultipartFile image) {
		return chatService.createImageMessage(loginUser.getUserId(), chatRoomId, image);
	}

	// 채팅방에서 상대를 차단한다. 두 사람이 함께 있는 모든 방에서 서로 보낼 수 없게 된다 (CHAT_010)
	// 이미 차단했어도 204다. 참여하지 않은 방이면 CHAT_ROOM_NOT_FOUND
	@PostMapping("/rooms/{chatRoomId}/block")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void createBlock(@AuthenticationPrincipal LoginUser loginUser, @PathVariable("chatRoomId") Long chatRoomId) {
		chatService.createBlock(loginUser.getUserId(), chatRoomId);
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

	// 방의 마지막 메시지까지 읽었다고 남긴다. 화면이 방을 열 때와 보는 중에 새 메시지가 올 때 부른다 (CHAT_007)
	// 돌려줄 내용이 없어 본문 없이 204로 답한다. 참여하지 않은 방이면 CHAT_ROOM_NOT_FOUND
	@PatchMapping("/rooms/{chatRoomId}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void updateRead(@AuthenticationPrincipal LoginUser loginUser, @PathVariable("chatRoomId") Long chatRoomId) {
		chatService.updateRead(loginUser.getUserId(), chatRoomId);
	}

	// 내가 걸어 둔 차단을 푼다. 차단한 적이 없어도 204다. 상대가 나를 차단한 것은 풀 수 없다 (CHAT_010)
	@DeleteMapping("/rooms/{chatRoomId}/block")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteBlock(@AuthenticationPrincipal LoginUser loginUser, @PathVariable("chatRoomId") Long chatRoomId) {
		chatService.deleteBlock(loginUser.getUserId(), chatRoomId);
	}
}
