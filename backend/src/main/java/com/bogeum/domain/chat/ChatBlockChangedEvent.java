package com.bogeum.domain.chat;

import java.util.List;

/** 채팅방에서 차단하거나 풀었다. 저장이 확정되면 ChatMessagePublisher가 두 사람에게 알린다 (CHAT_010) */
public record ChatBlockChangedEvent(Long chatRoomId, List<Long> userIds) {
}
