package com.bogeum.domain.chat;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.common.enums.ChatReportStatus;
import com.bogeum.domain.chat.model.ChatReport;

public interface ChatReportRepository extends JpaRepository<ChatReport, Long> {

	// 같은 사람이 같은 방을 처리 중에 다시 신고하지 않게 확인한다. 처리 완료된 신고는 빼서 다시 신고할 수 있다 (CHAT_011, CHAT_001)
	boolean existsByChatRoom_ChatRoomIdAndReporter_UserIdAndChatReportStatusNot(Long chatRoomId, Long reporterUserId,
		ChatReportStatus chatReportStatus);

	// 채팅 목록에서 방마다 내 신고 상태를 보여 주려고 내 신고를 한 번에 가져온다 (CHAT_001)
	List<ChatReport> findAllByReporter_UserId(Long reporterUserId);

	// 관리자 신고 목록. 방·매물·두 참여자·신고자를 함께 불러 줄마다 다시 조회하지 않는다 (CHAT_001)
	@EntityGraph(attributePaths = {"chatRoom", "chatRoom.property", "chatRoom.inquirer", "chatRoom.agent", "reporter"})
	Page<ChatReport> findAllBy(Pageable pageable);
}
