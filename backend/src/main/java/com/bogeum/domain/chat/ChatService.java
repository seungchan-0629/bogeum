package com.bogeum.domain.chat;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.chat.model.ChatDto;
import com.bogeum.domain.chat.model.ChatRoom;
import com.bogeum.domain.property.PropertyException;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {

	private final UserService userService;
	private final PropertyRepository propertyRepository;
	private final ChatRepository chatRepository;

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

	private Optional<ChatRoom> findSameRoom(ChatRoom chatRoom) {
		return chatRepository.findByProperty_PropertyIdAndInquirer_UserIdAndAgent_UserId(
			chatRoom.getProperty().getPropertyId(), chatRoom.getInquirer().getUserId(), chatRoom.getAgent().getUserId());
	}
}
