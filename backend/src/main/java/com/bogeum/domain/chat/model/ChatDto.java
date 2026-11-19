package com.bogeum.domain.chat.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.common.enums.ChatStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.MessageType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.PropertyType;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
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

	/** 내가 참여한 채팅방 전부. 최근 대화 순이다 (CHAT_005) */
	@Getter
	@AllArgsConstructor
	public static class RoomListResponse {

		private List<RoomItemResponse> rooms;
	}

	/** 채팅방 목록 한 줄 (CHAT_005, CHAT_007, CHAT_009) */
	@Getter
	@Builder
	public static class RoomItemResponse {

		private Long chatRoomId;
		private ChatRoomType roomType;
		private ChatStatus chatStatus;
		// 읽기 전용이면 매물이 삭제(DELETED)됐는지 계약(CONTRACTED)됐는지. 화면 안내 문구를 고른다
		private PropertyStatus readOnlyReason;
		private String partnerName;
		// 상대가 중개사일 때만 있다
		private String partnerOfficeName;
		private Long propertyId;
		private String roadAddress;
		private PropertyType propertyType;
		private LeaseType leaseType;
		private BigDecimal deposit;
		private BigDecimal monthlyRent;
		// 사진이면 "사진", 메시지가 없는 방은 비어 있다
		private String lastMessage;
		private LocalDateTime lastMessageAt;
		private Long unreadCount;
	}

	/** 채팅방의 대화 한 묶음. 오래된 것부터 담는다 (CHAT_006) */
	@Getter
	@AllArgsConstructor
	public static class MessageListResponse {

		private List<MessageItemResponse> messages;
		// 더 오래된 메시지를 받을 때 보낼 cursor. 더 없으면 비어 있다
		private Long nextCursor;
		private Boolean hasNext;
	}

	/** 메시지 하나. 사진 주소는 이미지 전송(CHAT_004)을 만들 때 더한다 (CHAT_006) */
	@Getter
	@Builder
	public static class MessageItemResponse {

		private Long chatMessageId;
		private Boolean isMine;
		private MessageType messageType;
		private String content;
		private LocalDateTime createdAt;

		public static MessageItemResponse of(ChatMessage message, Long userId) {
			return MessageItemResponse.builder()
				.chatMessageId(message.getChatMessageId())
				.isMine(message.getSender().getUserId().equals(userId))
				.messageType(message.getMessageType())
				.content(message.getContent())
				.createdAt(message.getCreatedAt())
				.build();
		}
	}
}
