package com.bogeum.domain.chat;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.domain.chat.model.ChatRoom;

public interface ChatRepository extends JpaRepository<ChatRoom, Long> {

	// 매물·문의자·중개사가 같은 방은 하나뿐이다 (chat_room 중복 금지 제약, CHAT_002)
	Optional<ChatRoom> findByProperty_PropertyIdAndInquirer_UserIdAndAgent_UserId(Long propertyId, Long inquirerUserId,
		Long agentUserId);

	// 계약 임차인 후보는 이 매물로 담당 중개사와 상담한 문의자다. 최근에 만든 채팅방부터 준다 (PROPERTY_011)
	@EntityGraph(attributePaths = "inquirer")
	List<ChatRoom> findAllByProperty_PropertyIdAndAgent_UserIdAndRoomTypeOrderByCreatedAtDesc(Long propertyId,
		Long agentUserId, ChatRoomType roomType);
}
