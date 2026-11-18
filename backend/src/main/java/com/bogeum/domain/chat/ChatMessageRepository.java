package com.bogeum.domain.chat;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bogeum.domain.chat.model.ChatMessage;
import com.bogeum.domain.chat.model.ChatRoomUnreadCount;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	// 처음 들어왔을 때의 최근 메시지 (CHAT_006)
	List<ChatMessage> findAllByChatRoom_ChatRoomIdOrderByChatMessageIdDesc(Long chatRoomId, Limit limit);

	// 위로 스크롤했을 때 cursor보다 오래된 메시지 (CHAT_006)
	List<ChatMessage> findAllByChatRoom_ChatRoomIdAndChatMessageIdLessThanOrderByChatMessageIdDesc(Long chatRoomId,
		Long cursor, Limit limit);

	// 방마다 마지막 메시지. 메시지 번호가 클수록 나중에 보낸 것이다 (CHAT_005)
	@Query("""
		select m from ChatMessage m
		where m.chatMessageId in (
			select max(latest.chatMessageId) from ChatMessage latest
			where latest.chatRoom.chatRoomId in :chatRoomIds
			group by latest.chatRoom.chatRoomId)
		""")
	List<ChatMessage> findLastMessages(@Param("chatRoomIds") List<Long> chatRoomIds);

	// 방마다 상대가 보낸 메시지 중 내가 마지막으로 읽은 메시지보다 뒤에 온 수. 안 읽은 방만 나온다 (CHAT_007)
	@Query("""
		select m.chatRoom.chatRoomId as chatRoomId, count(m) as unreadCount
		from ChatMessage m join m.chatRoom r
		where r.chatRoomId in :chatRoomIds
			and m.sender.userId <> :userId
			and m.chatMessageId > coalesce(case when r.inquirer.userId = :userId
				then r.inquirerLastReadMessageId else r.agentLastReadMessageId end, 0)
		group by m.chatRoom.chatRoomId
		""")
	List<ChatRoomUnreadCount> countUnreadMessages(@Param("chatRoomIds") List<Long> chatRoomIds,
		@Param("userId") Long userId);
}
