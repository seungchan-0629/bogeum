package com.bogeum.domain.chat.model;

/** 채팅방별 안 읽은 메시지 수 (CHAT_007) */
public interface ChatRoomUnreadCount {

	Long getChatRoomId();

	Long getUnreadCount();
}
