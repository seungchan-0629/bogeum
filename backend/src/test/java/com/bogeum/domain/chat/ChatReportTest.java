package com.bogeum.domain.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.bogeum.common.enums.ChatReportReason;
import com.bogeum.common.enums.ChatReportStatus;
import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.common.enums.ChatStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.chat.model.ChatReport;
import com.bogeum.domain.chat.model.ChatRoom;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

/**
 * 채팅방에서 상대를 신고하면 사유·내용이 남고, 같은 방은 다시 신고할 수 없는지 H2로 확인한다 (CHAT_011).
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-chat-report-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChatService.class)
@RecordApplicationEvents
class ChatReportTest {

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
	@Autowired
	private ApplicationEvents applicationEvents;
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
		chatRoom = room(ChatStatus.ACTIVE);
	}

	@Test
	@DisplayName("신고하면 방·신고자·사유·내용이 남고, 상대에게 알릴 이벤트는 나가지 않는다")
	void reportsPartner() {
		chatService.createReport(tenant.getUserId(), chatRoom.getChatRoomId(), ChatReportReason.ABUSIVE, "  욕설을 했어요  ");

		List<ChatReport> reports = chatReportRepository.findAll();
		assertEquals(1, reports.size());
		assertEquals(chatRoom.getChatRoomId(), reports.get(0).getChatRoom().getChatRoomId());
		assertEquals(tenant.getUserId(), reports.get(0).getReporter().getUserId());
		assertEquals(ChatReportReason.ABUSIVE, reports.get(0).getReasonType());
		assertEquals("욕설을 했어요", reports.get(0).getContent());
		assertEquals(0L, applicationEvents.stream(ChatBlockChangedEvent.class).count()
			+ applicationEvents.stream(ChatMessageCreatedEvent.class).count()
			+ applicationEvents.stream(ChatRoomsReadOnlyEvent.class).count());
	}

	@Test
	@DisplayName("내용은 선택이고 빈 내용은 비워서 남긴다. 기타 사유는 내용이 없으면 거절한다")
	void requiresContentOnlyForEtc() {
		chatService.createReport(tenant.getUserId(), chatRoom.getChatRoomId(), ChatReportReason.FRAUD, "   ");

		assertNull(chatReportRepository.findAll().get(0).getContent());
		for (String content : new String[] {null, "", "   "}) {
			assertErrorCode(ErrorCode.CHAT_REPORT_CONTENT_REQUIRED,
				() -> chatService.createReport(agent.getUserId(), chatRoom.getChatRoomId(), ChatReportReason.ETC, content));
		}
		assertEquals(1L, chatReportRepository.count());
	}

	@Test
	@DisplayName("같은 방을 다시 신고하면 거절하지만, 상대는 같은 방을 따로 신고할 수 있다")
	void rejectsDuplicateReport() {
		chatService.createReport(tenant.getUserId(), chatRoom.getChatRoomId(), ChatReportReason.FALSE_LISTING, null);

		assertErrorCode(ErrorCode.CHAT_ALREADY_REPORTED,
			() -> chatService.createReport(tenant.getUserId(), chatRoom.getChatRoomId(), ChatReportReason.FRAUD, null));
		chatService.createReport(agent.getUserId(), chatRoom.getChatRoomId(), ChatReportReason.ABUSIVE, null);
		assertEquals(2L, chatReportRepository.count());
	}

	@Test
	@DisplayName("처리 완료된 신고가 있으면 같은 방을 다시 신고할 수 있다 (CHAT_001)")
	void reportsAgainAfterResolved() {
		chatReportRepository.saveAndFlush(ChatReport.builder().chatRoom(chatRoom).reporter(tenant)
			.reasonType(ChatReportReason.ABUSIVE).chatReportStatus(ChatReportStatus.RESOLVED).build());

		chatService.createReport(tenant.getUserId(), chatRoom.getChatRoomId(), ChatReportReason.ABUSIVE, "또 욕설을 했어요");

		assertEquals(2L, chatReportRepository.count());
	}

	@Test
	@DisplayName("채팅 목록에는 내가 그 방에 한 가장 최근 신고의 상태가 나오고, 신고하지 않은 방은 비어 있다 (CHAT_001)")
	void showsReportStatusInRoomList() {
		ChatRoom otherRoom = room(ChatStatus.ACTIVE);
		chatReportRepository.saveAndFlush(ChatReport.builder().chatRoom(chatRoom).reporter(tenant)
			.reasonType(ChatReportReason.ABUSIVE).chatReportStatus(ChatReportStatus.RESOLVED).build());
		chatService.createReport(tenant.getUserId(), chatRoom.getChatRoomId(), ChatReportReason.FRAUD, null);
		chatService.readReportMessages(chatReportRepository.findAll().get(1).getChatReportId(), null);

		var rooms = chatService.readRooms(tenant.getUserId()).getRooms();
		assertEquals(ChatReportStatus.CHECKED, rooms.stream()
			.filter(room -> room.getChatRoomId().equals(chatRoom.getChatRoomId())).findFirst().orElseThrow().getReportStatus());
		assertNull(rooms.stream()
			.filter(room -> room.getChatRoomId().equals(otherRoom.getChatRoomId())).findFirst().orElseThrow().getReportStatus());
		// 신고당한 사람 목록에는 신고 상태가 나오지 않는다
		assertNull(chatService.readRooms(agent.getUserId()).getRooms().get(0).getReportStatus());
	}

	@Test
	@DisplayName("읽기 전용 방과 차단한 방도 신고할 수 있다")
	void reportsReadOnlyAndBlockedRoom() {
		ChatRoom readOnly = room(ChatStatus.READ_ONLY);
		chatService.createBlock(tenant.getUserId(), chatRoom.getChatRoomId());

		chatService.createReport(tenant.getUserId(), readOnly.getChatRoomId(), ChatReportReason.RISKY_PROPERTY, null);
		chatService.createReport(tenant.getUserId(), chatRoom.getChatRoomId(), ChatReportReason.ABUSIVE, null);

		assertEquals(2L, chatReportRepository.count());
	}

	@Test
	@DisplayName("참여하지 않은 방은 신고할 수 없다")
	void rejectsOthersRoom() {
		User stranger = user("오세입");

		assertErrorCode(ErrorCode.CHAT_ROOM_NOT_FOUND,
			() -> chatService.createReport(stranger.getUserId(), chatRoom.getChatRoomId(), ChatReportReason.ABUSIVE, null));
		assertEquals(0L, chatReportRepository.count());
	}

	private void assertErrorCode(ErrorCode errorCode, Executable executable) {
		assertEquals(errorCode, assertThrows(ChatException.class, executable).getErrorCode());
	}

	private ChatRoom room(ChatStatus chatStatus) {
		Property property = propertyRepository.saveAndFlush(Property.builder().landlord(user("박임대")).agent(agent)
			.leaseType(LeaseType.JEONSE).deposit(BigDecimal.valueOf(300_000_000)).roadAddress("서울특별시 마포구 월드컵로 61")
			.propertyStatus(PropertyStatus.ACTIVE).build());
		return chatRepository.saveAndFlush(ChatRoom.builder().property(property).roomType(ChatRoomType.TENANT_AGENT)
			.inquirer(tenant).agent(agent).chatStatus(chatStatus).build());
	}

	private User user(String name) {
		return userRepository.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash")
			.name(name).phone("010" + (int) (Math.random() * 100_000_000)).role(Role.USER).build());
	}
}
