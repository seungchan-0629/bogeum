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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
		// 상대가 어디까지 읽었는지. 이 번호보다 뒤에 있는 내 메시지에 "1"을 붙인다. 아무것도 안 읽었으면 비어 있다 (CHAT_007)
		private Long partnerLastReadMessageId;
	}

	/** 누가 어디까지 읽었는지. 방 구독자에게 보내 상대 화면의 "1"을 지운다 (CHAT_007) */
	@Getter
	@AllArgsConstructor
	public static class ReadResponse {

		private Long userId;
		private Long lastReadMessageId;
	}

	/** 보낼 글. 앞뒤 공백은 빼고 저장한다 (CHAT_003) */
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class MessageRequest {

		@NotBlank(message = "메시지를 입력해 주세요.")
		@Size(max = 2000, message = "메시지는 2,000자까지 보낼 수 있습니다.")
		private String content;
	}

	/** 메시지 하나 (CHAT_003, CHAT_004, CHAT_006) */
	@Getter
	@Builder
	public static class MessageItemResponse {

		private Long chatMessageId;
		// 방에 뿌린 메시지는 받는 사람마다 내 것인지가 달라서, 화면이 senderId로 판단한다
		private Long senderId;
		// 방에 뿌린 메시지에서는 비어 있다
		private Boolean isMine;
		private MessageType messageType;
		private String content;
		// 사진 메시지의 10분짜리 조회 주소. 글 메시지이거나 저장소가 없으면 비어 있다 (CHAT_004)
		private String imageUrl;
		private LocalDateTime createdAt;

		public static MessageItemResponse of(ChatMessage message, Long userId, String imageUrl) {
			return builderOf(message, imageUrl).isMine(message.getSender().getUserId().equals(userId)).build();
		}

		// 방 구독자 모두에게 같은 내용을 보낸다
		public static MessageItemResponse forRoom(ChatMessage message, String imageUrl) {
			return builderOf(message, imageUrl).build();
		}

		private static MessageItemResponseBuilder builderOf(ChatMessage message, String imageUrl) {
			return MessageItemResponse.builder()
				.chatMessageId(message.getChatMessageId())
				.senderId(message.getSender().getUserId())
				.messageType(message.getMessageType())
				.content(message.getContent())
				.imageUrl(imageUrl)
				.createdAt(message.getCreatedAt());
		}
	}
}
