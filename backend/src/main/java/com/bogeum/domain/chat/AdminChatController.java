package com.bogeum.domain.chat;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bogeum.domain.chat.model.ChatDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AdminChatController {

	private final ChatService chatService;

	// 관리자 신고 목록. 최근 신고부터 다섯 개씩, page는 1부터다 (CHAT_001)
	@GetMapping("/admin/chat-reports")
	public ChatDto.ReportListResponse readReports(@RequestParam(defaultValue = "1") int page) {
		return chatService.readReports(page);
	}

	// 신고된 방의 대화. 신고 번호로만 열 수 있고 처음 열면 신고가 확인 상태가 된다. 없는 신고면 CHAT_REPORT_NOT_FOUND (CHAT_001)
	@GetMapping("/admin/chat-reports/{chatReportId}/messages")
	public ChatDto.ReportMessageListResponse readReportMessages(@PathVariable("chatReportId") Long chatReportId,
			@RequestParam(value = "cursor", required = false) Long cursor) {
		return chatService.readReportMessages(chatReportId, cursor);
	}
}
