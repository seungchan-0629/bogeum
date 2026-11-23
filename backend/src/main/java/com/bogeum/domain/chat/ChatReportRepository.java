package com.bogeum.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.domain.chat.model.ChatReport;

public interface ChatReportRepository extends JpaRepository<ChatReport, Long> {

	// 같은 사람이 같은 방을 두 번 신고하지 않게 확인한다 (CHAT_011)
	boolean existsByChatRoom_ChatRoomIdAndReporter_UserId(Long chatRoomId, Long reporterUserId);
}
