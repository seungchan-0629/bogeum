package com.bogeum.domain.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.bogeum.common.enums.ChatReportReason;
import com.bogeum.common.enums.ChatReportStatus;
import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.common.enums.ChatStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.chat.model.ChatDto;
import com.bogeum.domain.chat.model.ChatReport;
import com.bogeum.domain.chat.model.ChatRoom;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

/**
 * 관리자가 신고 목록을 보고, 신고 번호로 그 방의 대화를 열람하면 신고가 확인 상태가 되는지 H2로 확인한다 (CHAT_001).
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-admin-chat-report-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChatService.class)
class AdminChatReportReadTest {

	@Autowired
	private ChatService chatService;
	@Autowired
	private ChatRepository chatRepository;
	@Autowired
	private ChatReportRepository chatReportRepository;
	@Autowired
	private PropertyRepository propertyRepository;
	@Autowired
	private UserRepository userRepository;
	// 채팅방 열기와 사진 보내기에만 쓰는 의존성
	@MockitoBean
	private UserService userService;
	@MockitoBean
	private ImageProcessor imageProcessor;

	private User tenant;
	private User agent;
	private ChatRoom chatRoom;

	@BeforeEach
	void setUp() {
		tenant = user("최세입");
		agent = user("김중개");
		chatRoom = room();
	}

	@Test
	@DisplayName("신고 목록은 최근 신고부터 다섯 개씩, 신고한 사람·신고당한 사람·매물 주소와 함께 나온다")
	void readsReportsNewestFirst() {
		chatService.createReport(tenant.getUserId(), chatRoom.getChatRoomId(), ChatReportReason.ABUSIVE, "욕설을 했어요");
		chatService.createReport(agent.getUserId(), chatRoom.getChatRoomId(), ChatReportReason.FRAUD, null);
		IntStream.range(0, 4).forEach(index -> {
			ChatRoom otherRoom = room();
			chatService.createReport(tenant.getUserId(), otherRoom.getChatRoomId(), ChatReportReason.ETC, "기타 " + index);
		});

		ChatDto.ReportListResponse first = chatService.readReports(1);
		ChatDto.ReportListResponse second = chatService.readReports(2);

		assertEquals(6L, first.getTotalElements());
		assertEquals(2, first.getTotalPages());
		assertEquals(5, first.getReports().size());
		assertEquals(1, second.getReports().size());
		ChatDto.ReportItemResponse oldest = second.getReports().get(0);
		assertEquals(ChatReportReason.ABUSIVE, oldest.getReasonType());
		assertEquals(ChatReportStatus.RECEIVED, oldest.getChatReportStatus());
		assertEquals("최세입", oldest.getReporterName());
		assertEquals(agent.getUserId(), oldest.getReportedUserId());
		assertEquals("김중개", oldest.getReportedName());
		assertEquals("서울특별시 마포구 월드컵로 61", oldest.getRoadAddress());
		// 중개사가 신고하면 신고당한 사람은 문의한 임차인이다
		ChatDto.ReportItemResponse byAgent = first.getReports().get(4);
		assertEquals(tenant.getUserId(), byAgent.getReportedUserId());
	}

	@Test
	@DisplayName("신고 번호로 그 방의 대화를 30개씩 보고, 처음 열면 신고가 확인 상태가 된다. 읽음 처리는 하지 않는다")
	void readsReportedRoomMessages() {
		IntStream.range(0, 31).forEach(index -> chatService.createMessage(
			index % 2 == 0 ? tenant.getUserId() : agent.getUserId(), chatRoom.getChatRoomId(), "메시지 " + index));
		chatService.createReport(tenant.getUserId(), chatRoom.getChatRoomId(), ChatReportReason.ABUSIVE, null);
		Long chatReportId = chatReportRepository.findAll().get(0).getChatReportId();

		ChatDto.ReportMessageListResponse latest = chatService.readReportMessages(chatReportId, null);
		ChatDto.ReportMessageListResponse older = chatService.readReportMessages(chatReportId, latest.getNextCursor());

		assertEquals(ChatRoomType.TENANT_AGENT, latest.getRoomType());
		assertEquals("최세입", latest.getInquirer().getName());
		assertEquals(agent.getUserId(), latest.getAgent().getUserId());
		assertEquals(30, latest.getMessages().size());
		assertTrue(latest.getHasNext());
		assertEquals("메시지 30", latest.getMessages().get(29).getContent());
		assertEquals(tenant.getUserId(), latest.getMessages().get(29).getSenderId());
		assertNull(latest.getMessages().get(29).getIsMine());
		assertEquals(List.of("메시지 0"), older.getMessages().stream().map(ChatDto.MessageItemResponse::getContent).toList());
		assertFalse(older.getHasNext());
		assertEquals(ChatReportStatus.CHECKED, chatReportRepository.findById(chatReportId).orElseThrow().getChatReportStatus());
		ChatRoom saved = chatRepository.findById(chatRoom.getChatRoomId()).orElseThrow();
		assertNull(saved.getInquirerLastReadMessageId());
		assertNull(saved.getAgentLastReadMessageId());
	}

	@Test
	@DisplayName("처리 완료된 신고는 다시 열어도 확인으로 돌아가지 않는다")
	void keepsResolvedStatus() {
		ChatReport resolved = chatReportRepository.saveAndFlush(ChatReport.builder().chatRoom(chatRoom).reporter(tenant)
			.reasonType(ChatReportReason.ABUSIVE).chatReportStatus(ChatReportStatus.RESOLVED).build());

		chatService.readReportMessages(resolved.getChatReportId(), null);

		assertEquals(ChatReportStatus.RESOLVED,
			chatReportRepository.findById(resolved.getChatReportId()).orElseThrow().getChatReportStatus());
	}

	@Test
	@DisplayName("없는 신고 번호는 404다")
	void rejectsMissingReport() {
		ChatException exception = assertThrows(ChatException.class, () -> chatService.readReportMessages(999L, null));

		assertEquals(ErrorCode.CHAT_REPORT_NOT_FOUND, exception.getErrorCode());
	}

	private ChatRoom room() {
		Property property = propertyRepository.saveAndFlush(Property.builder().landlord(user("박임대")).agent(agent)
			.leaseType(LeaseType.JEONSE).deposit(BigDecimal.valueOf(300_000_000)).roadAddress("서울특별시 마포구 월드컵로 61")
			.propertyStatus(PropertyStatus.ACTIVE).build());
		return chatRepository.saveAndFlush(ChatRoom.builder().property(property).roomType(ChatRoomType.TENANT_AGENT)
			.inquirer(tenant).agent(agent).chatStatus(ChatStatus.ACTIVE).build());
	}

	private User user(String name) {
		return userRepository.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash")
			.name(name).phone("010" + (int) (Math.random() * 100_000_000)).role(Role.USER).build());
	}
}
