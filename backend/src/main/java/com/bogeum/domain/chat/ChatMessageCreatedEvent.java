package com.bogeum.domain.chat;

import java.util.List;

import com.bogeum.domain.chat.model.ChatDto;

/** 채팅방에 메시지가 저장됐다. 저장이 확정되면 ChatMessagePublisher가 구독자에게 뿌린다 (CHAT_003) */
public record ChatMessageCreatedEvent(Long chatRoomId, ChatDto.MessageItemResponse message, List<Long> participantIds) {
}
