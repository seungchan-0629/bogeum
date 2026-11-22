package com.bogeum.domain.chat;

import java.util.List;

/** 매물이 삭제·계약돼 채팅방들이 읽기 전용이 됐다. 저장이 확정되면 ChatMessagePublisher가 참여자들에게 알린다 (CHAT_009) */
public record ChatRoomsReadOnlyEvent(List<Room> rooms) {

	public record Room(Long chatRoomId, List<Long> participantIds) {
	}
}
