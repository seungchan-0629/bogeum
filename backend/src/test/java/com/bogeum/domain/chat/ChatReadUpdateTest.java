package com.bogeum.domain.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.common.enums.ChatStatus;
import com.bogeum.common.enums.LeaseType;
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
 * 방에 들어오면 내 읽음 위치가 마지막 메시지로 가고, 상대가 어디까지 읽었는지 대화 내역에 나오는지 H2로 확인한다 (CHAT_007).
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-chat-read-update-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChatService.class)
@RecordApplicationEvents
class ChatReadUpdateTest {

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
		tenant = user("최세입");
		agent = user("김중개");
		chatRoom = room(ChatStatus.ACTIVE);
	}

	@Test
	@DisplayName("읽음 처리하면 내 읽음 위치만 방의 마지막 메시지가 되고, 목록의 안 읽은 수가 0이 된다")
	void marksOwnReadPosition() {
		message(agent, "안녕하세요");
		ChatMessage last = message(agent, "방문 가능하세요?");
		assertEquals(2L, chatService.readRooms(tenant.getUserId()).getRooms().get(0).getUnreadCount());

		chatService.updateRead(tenant.getUserId(), chatRoom.getChatRoomId());

		ChatRoom saved = chatRepository.findById(chatRoom.getChatRoomId()).orElseThrow();
		assertEquals(last.getChatMessageId(), saved.getInquirerLastReadMessageId());
		assertNull(saved.getAgentLastReadMessageId());
		assertEquals(0L, chatService.readRooms(tenant.getUserId()).getRooms().get(0).getUnreadCount());
	}

	@Test
	@DisplayName("읽음 처리하면 상대 화면의 \"1\"을 지울 이벤트가 나가고, 이미 다 읽었으면 다시 나가지 않는다")
	void publishesReadEventOnlyWhenChanged() {
		ChatMessage last = message(tenant, "주차 되나요?");

		chatService.updateRead(agent.getUserId(), chatRoom.getChatRoomId());
		chatService.updateRead(agent.getUserId(), chatRoom.getChatRoomId());

		List<ChatMessageReadEvent> events = applicationEvents.stream(ChatMessageReadEvent.class).toList();
		assertEquals(List.of(new ChatMessageReadEvent(chatRoom.getChatRoomId(), agent.getUserId(), last.getChatMessageId())), events);
	}

	@Test
	@DisplayName("읽음 위치는 앞으로만 간다")
	void neverMovesBack() {
		assertTrue(chatRoom.markRead(tenant.getUserId(), 10L));
		assertFalse(chatRoom.markRead(tenant.getUserId(), 5L));
		assertFalse(chatRoom.markRead(tenant.getUserId(), 10L));

		assertEquals(10L, chatRoom.getInquirerLastReadMessageId());
	}

	@Test
	@DisplayName("메시지가 없는 방은 바꿀 것이 없어 그대로 둔다")
	void ignoresEmptyRoom() {
		chatService.updateRead(tenant.getUserId(), chatRoom.getChatRoomId());

		assertNull(chatRepository.findById(chatRoom.getChatRoomId()).orElseThrow().getInquirerLastReadMessageId());
		assertEquals(0L, applicationEvents.stream(ChatMessageReadEvent.class).count());
	}

	@Test
	@DisplayName("읽기 전용 방도 남은 메시지를 읽음 처리할 수 있다")
	void readsReadOnlyRoom() {
		ChatRoom readOnly = room(ChatStatus.READ_ONLY);
		ChatMessage last = chatMessageRepository.saveAndFlush(ChatMessage.createText(readOnly, agent, "계약이 끝났어요"));

		chatService.updateRead(tenant.getUserId(), readOnly.getChatRoomId());

		assertEquals(last.getChatMessageId(), chatRepository.findById(readOnly.getChatRoomId()).orElseThrow().getInquirerLastReadMessageId());
	}

	@Test
	@DisplayName("참여하지 않은 방이나 없는 방은 읽음 처리할 수 없다")
	void rejectsOthersRoom() {
		User stranger = user("오세입");

		for (Long chatRoomId : new Long[] {chatRoom.getChatRoomId(), chatRoom.getChatRoomId() + 100}) {
			var exception = assertThrows(ChatException.class, () -> chatService.updateRead(stranger.getUserId(), chatRoomId));

			assertEquals(ErrorCode.CHAT_ROOM_NOT_FOUND, exception.getErrorCode());
		}
	}

	@Test
	@DisplayName("대화 내역에 상대가 어디까지 읽었는지 나온다")
	void showsPartnerReadPosition() {
		ChatMessage first = message(tenant, "주차 되나요?");
		message(tenant, "몇 대까지 되나요?");
		chatRoom.markRead(agent.getUserId(), first.getChatMessageId());
		chatRepository.saveAndFlush(chatRoom);

		var tenantView = chatService.readMessages(tenant.getUserId(), chatRoom.getChatRoomId(), null);
		var agentView = chatService.readMessages(agent.getUserId(), chatRoom.getChatRoomId(), null);

		assertEquals(first.getChatMessageId(), tenantView.getPartnerLastReadMessageId());
		// 임차인은 아직 아무것도 읽지 않았다
		assertNull(agentView.getPartnerLastReadMessageId());
	}

	private ChatMessage message(User sender, String content) {
		return chatMessageRepository.saveAndFlush(ChatMessage.createText(chatRoom, sender, content));
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
