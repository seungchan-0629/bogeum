package com.bogeum.domain.chat;

/** 채팅방에서 누가 어디까지 읽었다. 저장이 확정되면 ChatMessagePublisher가 알린다 (CHAT_007) */
public record ChatMessageReadEvent(Long chatRoomId, Long userId, Long lastReadMessageId) {
}
