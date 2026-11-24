package com.bogeum.domain.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.UUID;

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
 * 관리자가 처리 내용을 남기고 신고를 처리 완료하면, 신고한 사람이 같은 방을 다시 신고할 수 있는지 H2로 확인한다 (CHAT_012).
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-admin-chat-report-resolve-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChatService.class)
class AdminChatReportResolveTest {

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
		Property property = propertyRepository.saveAndFlush(Property.builder().landlord(user("박임대")).agent(agent)
			.leaseType(LeaseType.JEONSE).deposit(BigDecimal.valueOf(300_000_000)).roadAddress("서울특별시 마포구 월드컵로 61")
			.propertyStatus(PropertyStatus.ACTIVE).build());
		chatRoom = chatRepository.saveAndFlush(ChatRoom.builder().property(property).roomType(ChatRoomType.TENANT_AGENT)
			.inquirer(tenant).agent(agent).chatStatus(ChatStatus.ACTIVE).build());
	}

	@Test
	@DisplayName("처리 내용을 앞뒤 공백 없이 남기고 처리 완료하면, 신고한 사람이 같은 방을 다시 신고할 수 있다")
	void resolvesReport() {
		chatService.createReport(tenant.getUserId(), chatRoom.getChatRoomId(), ChatReportReason.ABUSIVE, "욕설을 했어요");
		Long chatReportId = chatReportRepository.findAll().get(0).getChatReportId();

		ChatDto.ReportItemResponse response = chatService.updateReportResolve(chatReportId, "  경고 조치했어요 ");

		assertEquals(ChatReportStatus.RESOLVED, response.getChatReportStatus());
		assertEquals("경고 조치했어요", response.getResolutionNote());
		assertNotNull(response.getResolvedAt());
		assertEquals(agent.getUserId(), response.getReportedUserId());
		ChatReport saved = chatReportRepository.findById(chatReportId).orElseThrow();
		assertEquals(ChatReportStatus.RESOLVED, saved.getChatReportStatus());
		assertEquals("경고 조치했어요", saved.getResolutionNote());
		chatService.createReport(tenant.getUserId(), chatRoom.getChatRoomId(), ChatReportReason.FRAUD, null);
		assertEquals(2, chatReportRepository.count());
	}

	@Test
	@DisplayName("대화를 열지 않은 접수 상태 신고도 처리 완료할 수 있다")
	void resolvesReceivedReport() {
		chatService.createReport(tenant.getUserId(), chatRoom.getChatRoomId(), ChatReportReason.FRAUD, null);
		ChatReport report = chatReportRepository.findAll().get(0);
		assertEquals(ChatReportStatus.RECEIVED, report.getChatReportStatus());

		chatService.updateReportResolve(report.getChatReportId(), "허위 신고로 확인");

		assertEquals(ChatReportStatus.RESOLVED,
			chatReportRepository.findById(report.getChatReportId()).orElseThrow().getChatReportStatus());
	}

	@Test
	@DisplayName("이미 처리 완료된 신고는 409이고 처리 내용은 바뀌지 않는다")
	void rejectsResolvedReport() {
		chatService.createReport(tenant.getUserId(), chatRoom.getChatRoomId(), ChatReportReason.ABUSIVE, null);
		Long chatReportId = chatReportRepository.findAll().get(0).getChatReportId();
		chatService.updateReportResolve(chatReportId, "경고 조치했어요");

		ChatException exception = assertThrows(ChatException.class,
			() -> chatService.updateReportResolve(chatReportId, "다시 처리"));

		assertEquals(ErrorCode.CHAT_REPORT_ALREADY_RESOLVED, exception.getErrorCode());
		assertEquals("경고 조치했어요", chatReportRepository.findById(chatReportId).orElseThrow().getResolutionNote());
	}

	@Test
	@DisplayName("없는 신고 번호는 404다")
	void rejectsMissingReport() {
		ChatException exception = assertThrows(ChatException.class, () -> chatService.updateReportResolve(999L, "처리"));

		assertEquals(ErrorCode.CHAT_REPORT_NOT_FOUND, exception.getErrorCode());
	}

	private User user(String name) {
		return userRepository.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash")
			.name(name).phone("010" + (int) (Math.random() * 100_000_000)).role(Role.USER).build());
	}
}
