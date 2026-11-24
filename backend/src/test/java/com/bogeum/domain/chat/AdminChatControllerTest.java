package com.bogeum.domain.chat;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bogeum.common.enums.ChatReportReason;
import com.bogeum.common.enums.ChatReportStatus;
import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.common.enums.MessageType;
import com.bogeum.domain.chat.model.ChatDto;

/** 관리자 신고 목록·대화 열람 요청의 주소·응답을 DB 없이 확인한다. 관리자 권한은 Security 설정이 맡는다 (CHAT_001). */
@WebMvcTest(AdminChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminChatControllerTest {

	private final MockMvc mockMvc;

	@MockitoBean
	private ChatService chatService;

	@Autowired
	AdminChatControllerTest(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	@DisplayName("신고 목록은 page가 없으면 1쪽을 주고, 있으면 그 쪽을 준다")
	void readsReports() throws Exception {
		when(chatService.readReports(1)).thenReturn(ChatDto.ReportListResponse.builder().reports(List.of(
			ChatDto.ReportItemResponse.builder().chatReportId(3L).chatReportStatus(ChatReportStatus.RECEIVED)
				.reasonType(ChatReportReason.ABUSIVE).reporterName("최세입").reportedUserId(7L).reportedName("김중개").build()))
			.page(1).totalPages(2).totalElements(6L).build());
		when(chatService.readReports(2)).thenReturn(ChatDto.ReportListResponse.builder().reports(List.of())
			.page(2).totalPages(2).totalElements(6L).build());

		mockMvc.perform(get("/admin/chat-reports"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reports[0].chatReportId").value(3))
			.andExpect(jsonPath("$.reports[0].chatReportStatus").value("RECEIVED"))
			.andExpect(jsonPath("$.reports[0].reportedUserId").value(7))
			.andExpect(jsonPath("$.totalPages").value(2));
		mockMvc.perform(get("/admin/chat-reports").param("page", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(2));
	}

	@Test
	@DisplayName("신고된 방의 대화는 cursor가 없으면 비워서, 있으면 그대로 넘긴다")
	void readsReportMessages() throws Exception {
		var response = new ChatDto.ReportMessageListResponse(ChatRoomType.TENANT_AGENT,
			new ChatDto.ParticipantResponse(2L, "최세입"), new ChatDto.ParticipantResponse(7L, "김중개"),
			List.of(ChatDto.MessageItemResponse.builder().chatMessageId(31L).senderId(2L)
				.messageType(MessageType.TEXT).content("안녕하세요").build()), 31L, true);
		when(chatService.readReportMessages(3L, null)).thenReturn(response);
		when(chatService.readReportMessages(3L, 31L)).thenReturn(response);

		mockMvc.perform(get("/admin/chat-reports/3/messages"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.inquirer.name").value("최세입"))
			.andExpect(jsonPath("$.agent.userId").value(7))
			.andExpect(jsonPath("$.messages[0].senderId").value(2))
			.andExpect(jsonPath("$.nextCursor").value(31));
		mockMvc.perform(get("/admin/chat-reports/3/messages").param("cursor", "31"))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("없는 신고 번호는 404다")
	void rejectsMissingReport() throws Exception {
		when(chatService.readReportMessages(999L, null)).thenThrow(ChatException.reportNotFound());

		mockMvc.perform(get("/admin/chat-reports/999/messages"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CHAT_REPORT_NOT_FOUND"));
	}
}
