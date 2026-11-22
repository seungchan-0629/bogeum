package com.bogeum.domain.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.common.enums.ChatStatus;
import com.bogeum.common.enums.MessageType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.agent.AgentRepository;
import com.bogeum.domain.agent.model.Agent;
import com.bogeum.domain.chat.model.ChatDto;
import com.bogeum.domain.chat.model.ChatMessage;
import com.bogeum.domain.chat.model.ChatRoom;
import com.bogeum.domain.chat.model.ChatRoomUnreadCount;
import com.bogeum.domain.property.PropertyException;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {

	// 대화 내역을 한 번에 불러오는 개수 (CHAT_006)
	private static final int MESSAGE_PAGE_SIZE = 30;
	// 최근 대화 순. 메시지가 없는 새 방은 만든 시각으로 줄을 세운다 (CHAT_005)
	private static final Comparator<ChatRoom> RECENT_ROOM_FIRST = Comparator
		.comparing((ChatRoom chatRoom) -> chatRoom.getLastMessageAt() != null ? chatRoom.getLastMessageAt() : chatRoom.getCreatedAt())
		.thenComparing(ChatRoom::getChatRoomId)
		.reversed();

	private final UserService userService;
	private final PropertyRepository propertyRepository;
	private final ChatRepository chatRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final AgentRepository agentRepository;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 매물로 대화할 채팅방을 열고 번호를 준다. 이미 있으면 기존 방이다 (CHAT_002).
	 * 같은 버튼이어도 누른 사람과 매물의 관계로 방을 고른다.
	 * - 중개사: 맡은 매물의 임대인–중개사 방
	 * - 임대인(본인 매물): 담당 중개사와의 임대인–중개사 방
	 * - 임차인: 공개 중인 매물의 임차인–중개사 방
	 * 트랜잭션을 걸지 않는다. 동시에 눌러 저장이 겹치면 그 저장만 되돌리고 먼저 만든 방을 다시 찾기 위해서다 (관심 매물 담기와 같은 방식)
	 */
	public ChatDto.RoomResponse createRoom(Long userId, Long propertyId) {
		User user = userService.findUser(userId);
		ChatRoom chatRoom = user.getRole() == Role.AGENT
			? openAgentRoom(userId, propertyId)
			: openUserRoom(user, propertyId);
		return new ChatDto.RoomResponse(chatRoom.getChatRoomId());
	}

	/** 중개 수락과 같은 트랜잭션에서 임대인–중개사 방을 만든다. 이미 있으면 그대로 둔다 (CHAT_002) */
	public void createBrokerageRoom(Property property) {
		ChatRoom chatRoom = ChatRoom.createBrokerageRoom(property);
		if (findSameRoom(chatRoom).isEmpty()) {
			chatRepository.save(chatRoom);
		}
	}

	/**
	 * 채팅방에 글 메시지를 저장하고, 방을 보고 있는 사람과 두 참여자의 채팅 목록에 알린다 (CHAT_003).
	 * 참여하지 않은 방은 CHAT_ROOM_NOT_FOUND, 읽기 전용 방은 CHAT_ROOM_READ_ONLY (CHAT_009)
	 */
	@Transactional
	public ChatDto.MessageItemResponse createMessage(Long userId, Long chatRoomId, String content) {
		ChatRoom chatRoom = findParticipantRoom(chatRoomId, userId);
		if (chatRoom.getChatStatus() == ChatStatus.READ_ONLY) {
			throw ChatException.chatRoomReadOnly();
		}
		User sender = chatRoom.getAgent().getUserId().equals(userId) ? chatRoom.getAgent() : chatRoom.getInquirer();
		ChatMessage message = chatMessageRepository.saveAndFlush(ChatMessage.createText(chatRoom, sender, content.strip()));
		chatRoom.recordMessage(message.getCreatedAt());
		// 구독자에게 뿌리는 일은 저장이 확정된 뒤 ChatMessagePublisher가 한다
		eventPublisher.publishEvent(new ChatMessageCreatedEvent(chatRoomId, ChatDto.MessageItemResponse.forRoom(message),
			List.of(chatRoom.getInquirer().getUserId(), chatRoom.getAgent().getUserId())));
		return ChatDto.MessageItemResponse.of(message, userId);
	}

	/**
	 * 내가 참여한 채팅방 전부를 최근 대화 순으로 준다 (CHAT_005).
	 * 방마다 마지막 메시지와 안 읽은 메시지 수(CHAT_007)를 붙인다. 방 수와 상관없이 쿼리 네 번으로 끝낸다
	 */
	@Transactional(readOnly = true)
	public ChatDto.RoomListResponse readRooms(Long userId) {
		List<ChatRoom> chatRooms = chatRepository.findAllByInquirer_UserIdOrAgent_UserId(userId, userId);
		if (chatRooms.isEmpty()) {
			return new ChatDto.RoomListResponse(List.of());
		}
		List<Long> chatRoomIds = chatRooms.stream().map(ChatRoom::getChatRoomId).toList();
		Map<Long, ChatMessage> lastMessages = chatMessageRepository.findLastMessages(chatRoomIds).stream()
			.collect(Collectors.toMap(message -> message.getChatRoom().getChatRoomId(), Function.identity()));
		Map<Long, Long> unreadCounts = chatMessageRepository.countUnreadMessages(chatRoomIds, userId).stream()
			.collect(Collectors.toMap(ChatRoomUnreadCount::getChatRoomId, ChatRoomUnreadCount::getUnreadCount));
		Map<Long, String> officeNames = readOfficeNames(chatRooms, userId);
		List<ChatDto.RoomItemResponse> rooms = chatRooms.stream()
			.sorted(RECENT_ROOM_FIRST)
			.map(chatRoom -> toRoomItem(chatRoom, userId, lastMessages.get(chatRoom.getChatRoomId()),
				unreadCounts.getOrDefault(chatRoom.getChatRoomId(), 0L), officeNames))
			.toList();
		return new ChatDto.RoomListResponse(rooms);
	}

	/**
	 * 채팅방의 이전 대화를 30개씩 준다 (CHAT_006).
	 * cursor가 없으면 최근 30개, 있으면 그보다 오래된 30개다. 방에 참여한 사람만 볼 수 있다
	 */
	@Transactional(readOnly = true)
	public ChatDto.MessageListResponse readMessages(Long userId, Long chatRoomId, Long cursor) {
		ChatRoom chatRoom = findParticipantRoom(chatRoomId, userId);
		// 하나 더 받아서 더 오래된 메시지가 남았는지 안다
		Limit limit = Limit.of(MESSAGE_PAGE_SIZE + 1);
		List<ChatMessage> found = cursor == null
			? chatMessageRepository.findAllByChatRoom_ChatRoomIdOrderByChatMessageIdDesc(chatRoomId, limit)
			: chatMessageRepository.findAllByChatRoom_ChatRoomIdAndChatMessageIdLessThanOrderByChatMessageIdDesc(
				chatRoomId, cursor, limit);
		boolean hasNext = found.size() > MESSAGE_PAGE_SIZE;
		List<ChatMessage> messages = new ArrayList<>(found.subList(0, Math.min(found.size(), MESSAGE_PAGE_SIZE)));
		// 화면은 위에서 아래로 오래된 것부터 그린다
		Collections.reverse(messages);
		Long nextCursor = hasNext ? messages.get(0).getChatMessageId() : null;
		return new ChatDto.MessageListResponse(
			messages.stream().map(message -> ChatDto.MessageItemResponse.of(message, userId)).toList(), nextCursor, hasNext,
			chatRoom.partnerLastReadMessageId(userId));
	}

	/**
	 * 방의 마지막 메시지까지 읽었다고 남기고, 상대 화면의 "1"과 내 채팅 목록의 안 읽은 수를 바로 바꾸게 알린다 (CHAT_007).
	 * 읽기 전용 방도 남은 메시지를 읽을 수 있어 처리한다. 메시지가 없거나 이미 다 읽었으면 바꾸지 않는다
	 */
	@Transactional
	public void updateRead(Long userId, Long chatRoomId) {
		ChatRoom chatRoom = findParticipantRoom(chatRoomId, userId);
		Long lastMessageId = chatMessageRepository.findLastMessageId(chatRoomId);
		if (lastMessageId != null && chatRoom.markRead(userId, lastMessageId)) {
			eventPublisher.publishEvent(new ChatMessageReadEvent(chatRoomId, userId, lastMessageId));
		}
	}

	/** 임대인이 매물을 삭제하면 매물 삭제와 같은 트랜잭션에서 그 매물의 방을 모두 읽기 전용으로 바꾼다 (CHAT_009) */
	@Transactional
	public void updateReadOnlyByDelete(Long propertyId) {
		changeToReadOnly(chatRepository.findAllByProperty_PropertyId(propertyId));
	}

	/**
	 * 중개사가 계약을 등록하면 계약 등록과 같은 트랜잭션에서 계약하지 않은 문의자의 방을 읽기 전용으로 바꾼다 (CHAT_009).
	 * 계약한 임차인–중개사 방과 임대인–중개사 방은 잔금·입주 이야기를 이어 가도록 그대로 둔다
	 */
	@Transactional
	public void updateReadOnlyByContract(Long propertyId, Long tenantUserId) {
		changeToReadOnly(chatRepository.findAllByProperty_PropertyId(propertyId).stream()
			.filter(chatRoom -> chatRoom.getRoomType() == ChatRoomType.TENANT_AGENT
				&& !chatRoom.getInquirer().getUserId().equals(tenantUserId))
			.toList());
	}

	private ChatRoom openAgentRoom(Long agentUserId, Long propertyId) {
		Property property = propertyRepository.findByPropertyIdAndAgent_UserId(propertyId, agentUserId)
			.filter(found -> found.getPropertyStatus() != PropertyStatus.DELETED)
			.orElseThrow(PropertyException::propertyNotFound);
		// 예전 데이터처럼 수락은 됐는데 방이 없으면 이때 만든다
		return findOrSaveRoom(ChatRoom.createBrokerageRoom(property));
	}

	private ChatRoom openUserRoom(User user, Long propertyId) {
		Property property = propertyRepository.findById(propertyId)
			.filter(found -> found.getPropertyStatus() != PropertyStatus.DELETED)
			.orElseThrow(PropertyException::propertyNotFound);
		if (property.getLandlord().getUserId().equals(user.getUserId())) {
			// 중개사가 수락하기 전에는 대화할 상대가 없다
			if (property.getAgent() == null) {
				throw ChatException.agentNotAssigned();
			}
			return findOrSaveRoom(ChatRoom.createBrokerageRoom(property));
		}
		// 공개 전·숨김·계약된 매물은 상세도 열리지 않아 문의할 수 없다
		if (property.getPropertyStatus() != PropertyStatus.ACTIVE) {
			throw PropertyException.propertyNotFound();
		}
		return findOrSaveRoom(ChatRoom.createInquiryRoom(property, user));
	}

	private ChatRoom findOrSaveRoom(ChatRoom newRoom) {
		return findSameRoom(newRoom).orElseGet(() -> {
			try {
				return chatRepository.saveAndFlush(newRoom);
			} catch (DataIntegrityViolationException exception) {
				// 확인과 저장 사이에 같은 요청이 먼저 만든 경우만 그 방을 준다. 그 밖의 제약 오류는 그대로 올린다
				return findSameRoom(newRoom).orElseThrow(() -> exception);
			}
		});
	}

	// 내가 문의자인 방의 상대는 중개사라서 사무소 이름을 같이 보여 준다
	private Map<Long, String> readOfficeNames(List<ChatRoom> chatRooms, Long userId) {
		Set<Long> agentUserIds = chatRooms.stream()
			.filter(chatRoom -> !chatRoom.getAgent().getUserId().equals(userId))
			.map(chatRoom -> chatRoom.getAgent().getUserId())
			.collect(Collectors.toSet());
		if (agentUserIds.isEmpty()) {
			return Map.of();
		}
		return agentRepository.findAllByUser_UserIdIn(agentUserIds).stream()
			.collect(Collectors.toMap(agent -> agent.getUser().getUserId(), Agent::getOfficeName));
	}

	private ChatDto.RoomItemResponse toRoomItem(ChatRoom chatRoom, Long userId, ChatMessage lastMessage, Long unreadCount,
			Map<Long, String> officeNames) {
		User partner = chatRoom.getAgent().getUserId().equals(userId) ? chatRoom.getInquirer() : chatRoom.getAgent();
		Property property = chatRoom.getProperty();
		return ChatDto.RoomItemResponse.builder()
			.chatRoomId(chatRoom.getChatRoomId())
			.roomType(chatRoom.getRoomType())
			.chatStatus(chatRoom.getChatStatus())
			.readOnlyReason(chatRoom.getChatStatus() == ChatStatus.READ_ONLY ? property.getPropertyStatus() : null)
			.partnerName(partner.getName())
			.partnerOfficeName(officeNames.get(partner.getUserId()))
			.propertyId(property.getPropertyId())
			.roadAddress(property.getRoadAddress())
			.propertyType(property.getPropertyType())
			.leaseType(property.getLeaseType())
			.deposit(property.getDeposit())
			.monthlyRent(property.getMonthlyRent())
			.lastMessage(toPreview(lastMessage))
			.lastMessageAt(chatRoom.getLastMessageAt())
			.unreadCount(unreadCount)
			.build();
	}

	private String toPreview(ChatMessage message) {
		if (message == null) {
			return null;
		}
		return message.getMessageType() == MessageType.IMAGE ? "사진" : message.getContent();
	}

	// 이미 읽기 전용인 방은 건너뛰고, 바뀐 방의 두 참여자에게만 열어 둔 화면을 갱신하라고 알린다
	private void changeToReadOnly(List<ChatRoom> chatRooms) {
		List<ChatRoomsReadOnlyEvent.Room> changed = chatRooms.stream()
			.filter(ChatRoom::changeToReadOnly)
			.map(chatRoom -> new ChatRoomsReadOnlyEvent.Room(chatRoom.getChatRoomId(),
				List.of(chatRoom.getInquirer().getUserId(), chatRoom.getAgent().getUserId())))
			.toList();
		if (!changed.isEmpty()) {
			eventPublisher.publishEvent(new ChatRoomsReadOnlyEvent(changed));
		}
	}

	// 참여하지 않은 방은 없는 방과 똑같이 CHAT_ROOM_NOT_FOUND로 거절한다
	private ChatRoom findParticipantRoom(Long chatRoomId, Long userId) {
		return chatRepository.findById(chatRoomId)
			.filter(chatRoom -> isParticipant(chatRoom, userId))
			.orElseThrow(ChatException::chatRoomNotFound);
	}

	private boolean isParticipant(ChatRoom chatRoom, Long userId) {
		return chatRoom.getInquirer().getUserId().equals(userId) || chatRoom.getAgent().getUserId().equals(userId);
	}

	private Optional<ChatRoom> findSameRoom(ChatRoom chatRoom) {
		return chatRepository.findByProperty_PropertyIdAndInquirer_UserIdAndAgent_UserId(
			chatRoom.getProperty().getPropertyId(), chatRoom.getInquirer().getUserId(), chatRoom.getAgent().getUserId());
	}
}
