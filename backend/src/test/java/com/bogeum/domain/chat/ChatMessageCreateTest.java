package com.bogeum.domain.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.common.enums.ChatStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.MessageType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.chat.model.ChatMessage;
import com.bogeum.domain.chat.model.ChatRoom;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

/**
 * 메시지가 저장되고 방의 마지막 대화 시각이 바뀌며, 구독자에게 뿌릴 이벤트가 나가는지 H2로 확인한다 (CHAT_003).
 * 실제로 뿌리는 일은 저장이 확정된 뒤 ChatMessagePublisher가 한다
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-chat-message-create-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChatService.class)
@RecordApplicationEvents
class ChatMessageCreateTest {

	@Autowired
	private ChatService chatService;
	@Autowired
	private ChatRepository chatRepository;
	@Autowired
	private ChatMessageRepository chatMessageRepository;
	@Autowired
	private PropertyRepository propertyRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ApplicationEvents applicationEvents;
	// 채팅방 열기에만 쓰는 의존성
	@MockitoBean
	private UserService userService;
	@MockitoBean
	private ImageProcessor imageProcessor;

	private User tenant;
	private User agent;
	private ChatRoom chatRoom;

	@BeforeEach
	void setUp() {
		User landlord = user("박임대");
		tenant = user("최세입");
		agent = user("김중개");
		chatRoom = room(ChatStatus.ACTIVE, landlord);
	}

	@Test
	@DisplayName("보낸 글은 앞뒤 공백을 빼고 저장되고, 방의 마지막 대화 시각이 그 메시지 시각이 된다")
	void savesMessageAndRecordsTime() {
		var response = chatService.createMessage(tenant.getUserId(), chatRoom.getChatRoomId(), "  주차 되나요?  ");

		ChatMessage saved = chatMessageRepository.findById(response.getChatMessageId()).orElseThrow();
		assertEquals("주차 되나요?", saved.getContent());
		assertEquals(MessageType.TEXT, saved.getMessageType());
		assertEquals(tenant.getUserId(), saved.getSender().getUserId());
		assertEquals(saved.getCreatedAt(), chatRepository.findById(chatRoom.getChatRoomId()).orElseThrow().getLastMessageAt());
		assertTrue(response.getIsMine());
		assertEquals(tenant.getUserId(), response.getSenderId());
	}

	@Test
	@DisplayName("저장하면 방 구독자에게 뿌릴 메시지와 두 참여자의 목록 갱신 이벤트가 나간다")
	void publishesCreatedEvent() {
		var response = chatService.createMessage(agent.getUserId(), chatRoom.getChatRoomId(), "내일 3시 어떠세요?");

		List<ChatMessageCreatedEvent> events = applicationEvents.stream(ChatMessageCreatedEvent.class).toList();
		assertEquals(1, events.size());
		ChatMessageCreatedEvent event = events.get(0);
		assertEquals(chatRoom.getChatRoomId(), event.chatRoomId());
		assertEquals(response.getChatMessageId(), event.message().getChatMessageId());
		assertEquals(agent.getUserId(), event.message().getSenderId());
		// 받는 사람마다 내 것인지가 달라 비워 둔다
		assertNull(event.message().getIsMine());
		assertEquals(List.of(tenant.getUserId(), agent.getUserId()), event.participantIds());
	}

	@Test
	@DisplayName("참여하지 않은 방이나 없는 방에는 보낼 수 없다")
	void rejectsOthersRoom() {
		User stranger = user("오세입");

		for (Long chatRoomId : new Long[] {chatRoom.getChatRoomId(), chatRoom.getChatRoomId() + 100}) {
			var exception = assertThrows(ChatException.class,
				() -> chatService.createMessage(stranger.getUserId(), chatRoomId, "안녕하세요"));

			assertEquals(ErrorCode.CHAT_ROOM_NOT_FOUND, exception.getErrorCode());
		}
		assertEquals(0L, chatMessageRepository.count());
		assertEquals(0L, applicationEvents.stream(ChatMessageCreatedEvent.class).count());
	}

	@Test
	@DisplayName("사진 저장소가 없는 환경에서는 사진을 보낼 수 없고, 대화 내역의 사진 주소는 비어 있다 (CHAT_004)")
	void rejectsImageWithoutStorage() {
		var exception = assertThrows(ChatException.class, () -> chatService.createImageMessage(tenant.getUserId(),
			chatRoom.getChatRoomId(), new MockMultipartFile("image", "defect.png", "image/png", new byte[] {9})));
		chatMessageRepository.saveAndFlush(ChatMessage.createImage(chatRoom, tenant, "chat-images/1/a.jpg"));

		assertEquals(ErrorCode.CHAT_STORAGE_UNAVAILABLE, exception.getErrorCode());
		assertNull(chatService.readMessages(agent.getUserId(), chatRoom.getChatRoomId(), null).getMessages().get(0).getImageUrl());
	}

	@Test
	@DisplayName("읽기 전용 방에는 보낼 수 없다")
	void rejectsReadOnlyRoom() {
		ChatRoom readOnly = room(ChatStatus.READ_ONLY, user("이임대"));

		var exception = assertThrows(ChatException.class,
			() -> chatService.createMessage(tenant.getUserId(), readOnly.getChatRoomId(), "아직 계약 가능한가요?"));

		assertEquals(ErrorCode.CHAT_ROOM_READ_ONLY, exception.getErrorCode());
		assertEquals(0L, chatMessageRepository.count());
	}

	@Test
	@DisplayName("보낸 메시지가 채팅 목록의 마지막 메시지가 된다")
	void becomesLastMessageInRoomList() {
		chatService.createMessage(tenant.getUserId(), chatRoom.getChatRoomId(), "첫 메시지");
		chatService.createMessage(agent.getUserId(), chatRoom.getChatRoomId(), "답장");

		var room = chatService.readRooms(tenant.getUserId()).getRooms().get(0);

		assertEquals("답장", room.getLastMessage());
		assertEquals(1L, room.getUnreadCount());
	}

	private ChatRoom room(ChatStatus chatStatus, User landlord) {
		Property property = propertyRepository.saveAndFlush(Property.builder().landlord(landlord).agent(agent)
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
