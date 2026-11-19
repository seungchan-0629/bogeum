package com.bogeum.domain.chat;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.domain.chat.model.ChatRoom;

public interface ChatRepository extends JpaRepository<ChatRoom, Long> {

	// 내가 문의자이거나 중개사인 방 전부. 목록에 매물 요약과 상대 이름을 보여 줘서 함께 가져온다 (CHAT_005)
	@EntityGraph(attributePaths = {"property", "inquirer", "agent"})
	List<ChatRoom> findAllByInquirer_UserIdOrAgent_UserId(Long inquirerUserId, Long agentUserId);

	// 방 대화를 구독하려는 사람이 그 방의 문의자나 중개사인지 확인한다 (CHAT_003)
	@Query("""
		select count(r) > 0 from ChatRoom r
		where r.chatRoomId = :chatRoomId and (r.inquirer.userId = :userId or r.agent.userId = :userId)
		""")
	boolean existsParticipant(@Param("chatRoomId") Long chatRoomId, @Param("userId") Long userId);

	// 매물·문의자·중개사가 같은 방은 하나뿐이다 (chat_room 중복 금지 제약, CHAT_002)
	Optional<ChatRoom> findByProperty_PropertyIdAndInquirer_UserIdAndAgent_UserId(Long propertyId, Long inquirerUserId,
		Long agentUserId);

	// 계약 임차인 후보는 이 매물로 담당 중개사와 상담한 문의자다. 최근에 만든 채팅방부터 준다 (PROPERTY_011)
	@EntityGraph(attributePaths = "inquirer")
	List<ChatRoom> findAllByProperty_PropertyIdAndAgent_UserIdAndRoomTypeOrderByCreatedAtDesc(Long propertyId,
		Long agentUserId, ChatRoomType roomType);
}
