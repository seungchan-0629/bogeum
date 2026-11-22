package com.bogeum.domain.chat;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bogeum.domain.chat.model.ChatBlock;

public interface ChatBlockRepository extends JpaRepository<ChatBlock, Long> {

	// 내가 상대를 차단했는지. 차단·해제할 때 쓴다 (CHAT_010)
	Optional<ChatBlock> findByBlocker_UserIdAndBlocked_UserId(Long blockerUserId, Long blockedUserId);

	// 두 사람 중 누가 누구를 차단했든 메시지를 보낼 수 없다 (CHAT_010)
	@Query("""
		select count(b) > 0 from ChatBlock b
		where (b.blocker.userId = :userId and b.blocked.userId = :partnerUserId)
			or (b.blocker.userId = :partnerUserId and b.blocked.userId = :userId)
		""")
	boolean existsBetween(@Param("userId") Long userId, @Param("partnerUserId") Long partnerUserId);

	// 채팅 목록에서 방마다 차단 상태를 보여 주려고 내가 하거나 당한 차단을 한 번에 가져온다 (CHAT_010)
	List<ChatBlock> findAllByBlocker_UserIdOrBlocked_UserId(Long blockerUserId, Long blockedUserId);
}
