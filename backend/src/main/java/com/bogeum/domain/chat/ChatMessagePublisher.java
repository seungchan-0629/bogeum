package com.bogeum.domain.chat;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.bogeum.domain.chat.model.ChatDto;

import lombok.RequiredArgsConstructor;

/**
 * 저장된 메시지를 STOMP 구독자에게 뿌린다 (CHAT_003, CHAT_005).
 * 뿌리는 곳을 여기 한 곳에 모아, 서버를 늘려 외부 중계로 바꿀 때 이 클래스만 고치면 되게 한다
 */
@Component
@RequiredArgsConstructor
public class ChatMessagePublisher {

	private final SimpMessagingTemplate messagingTemplate;

	// 저장이 확정된 뒤에만 보낸다. 저장이 되돌려졌는데 상대 화면에 메시지가 뜨지 않게 한다
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(ChatMessageCreatedEvent event) {
		// 방을 보고 있는 사람에게 새 메시지를 보낸다
		messagingTemplate.convertAndSend("/topic/chat-rooms/" + event.chatRoomId(), event.message());
		// 두 참여자의 채팅 목록 순서·마지막 메시지·안 읽은 수가 바뀌었다고 알린다. 화면은 목록을 다시 불러온다
		event.participantIds().forEach(userId -> publishRoomUpdated(userId, event.chatRoomId()));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(ChatMessageReadEvent event) {
		// 상대 화면에서 이 번호까지 내 메시지의 "1"을 지운다
		messagingTemplate.convertAndSend("/topic/chat-rooms/" + event.chatRoomId() + "/read",
			new ChatDto.ReadResponse(event.userId(), event.lastReadMessageId()));
		// 읽은 사람의 다른 창에서도 안 읽은 수가 0이 되게 목록을 다시 불러오게 한다
		publishRoomUpdated(event.userId(), event.chatRoomId());
	}

	// 열어 둔 화면은 목록을 다시 불러와 그 방의 입력창이 안내 문구로 바뀐다
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(ChatRoomsReadOnlyEvent event) {
		event.rooms().forEach(room -> room.participantIds().forEach(userId -> publishRoomUpdated(userId, room.chatRoomId())));
	}

	// 두 사람의 화면이 목록을 다시 불러와 입력창 자리에 차단 안내가 생기거나 사라진다
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(ChatBlockChangedEvent event) {
		event.userIds().forEach(userId -> publishRoomUpdated(userId, event.chatRoomId()));
	}

	private void publishRoomUpdated(Long userId, Long chatRoomId) {
		messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/chat-rooms", new ChatDto.RoomResponse(chatRoomId));
	}
}
