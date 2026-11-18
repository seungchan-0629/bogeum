package com.bogeum.domain.chat.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ChatDto {

	/** 대화할 매물. 방 종류는 누른 사람과 매물의 관계로 서버가 고른다 (CHAT_002) */
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RoomRequest {

		@NotNull(message = "매물을 선택해 주세요.")
		private Long propertyId;
	}

	/** 열린 채팅방 번호. 새로 만든 방이든 기존 방이든 같다 (CHAT_002) */
	@Getter
	@AllArgsConstructor
	public static class RoomResponse {

		private Long chatRoomId;
	}
}
