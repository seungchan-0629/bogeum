package com.bogeum.domain.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

import com.bogeum.common.enums.ApprovalStatus;
import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.common.enums.ChatStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.MessageType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.agent.AgentRepository;
import com.bogeum.domain.agent.model.Agent;
import com.bogeum.domain.chat.model.ChatDto;
import com.bogeum.domain.chat.model.ChatMessage;
import com.bogeum.domain.chat.model.ChatRoom;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

/**
 * 채팅방 목록이 내 방만 최근 대화 순으로 나오고, 대화 내역이 30개씩 이어지는지 H2로 확인한다 (CHAT_005, CHAT_006, CHAT_007).
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-chat-room-read-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChatService.class)
class ChatRoomReadTest {

	// 메시지가 없는 새 방은 실제 만든 시각으로 줄을 서서, 대화 시각은 그보다 앞으로 둔다
	private static final LocalDateTime NOW = LocalDateTime.now().minusDays(1).withNano(0);

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
	private AgentRepository agentRepository;
	// 채팅방 열기에만 쓰는 의존성
	@MockitoBean
	private UserService userService;
	@MockitoBean
	private ImageProcessor imageProcessor;

	private User landlord;
	private User tenant;
	private User agent;

	@BeforeEach
	void setUp() {
		landlord = user("박임대", Role.USER);
		tenant = user("최세입", Role.USER);
		agent = user("김중개", Role.AGENT);
		agentRepository.saveAndFlush(Agent.builder().user(agent).officeName("망원 행복공인중개사사무소")
			.officeAddress("서울특별시 마포구 망원로 1").approvalStatus(ApprovalStatus.APPROVED).build());
	}

	@Test
	@DisplayName("채팅방 목록은 내 방만 최근 대화 순으로 주고, 메시지가 없는 방은 만든 시각으로 줄을 세운다")
	void readsOwnRoomsRecentFirst() {
		ChatRoom old = room(property(PropertyStatus.ACTIVE), tenant, ChatRoomType.TENANT_AGENT, NOW.minusDays(3));
		ChatRoom recent = room(property(PropertyStatus.ACTIVE), tenant, ChatRoomType.TENANT_AGENT, NOW.minusHours(1));
		ChatRoom empty = room(property(PropertyStatus.ACTIVE), tenant, ChatRoomType.TENANT_AGENT, null);
		room(property(PropertyStatus.ACTIVE), user("오세입", Role.USER), ChatRoomType.TENANT_AGENT, NOW);

		var rooms = chatService.readRooms(tenant.getUserId()).getRooms();

		// 새 방은 방금 만들어 어제·사흘 전 대화보다 위에 온다
		assertEquals(List.of(empty.getChatRoomId(), recent.getChatRoomId(), old.getChatRoomId()),
			rooms.stream().map(ChatDto.RoomItemResponse::getChatRoomId).toList());
		assertNull(rooms.get(0).getLastMessage());
	}

	@Test
	@DisplayName("문의자에게는 상대 중개사의 이름·사무소, 중개사에게는 상대 이름만 보이고 마지막 메시지를 미리 보여 준다")
	void showsPartnerAndLastMessage() {
		Property property = property(PropertyStatus.ACTIVE);
		ChatRoom chatRoom = room(property, landlord, ChatRoomType.LANDLORD_AGENT, NOW);
		message(chatRoom, landlord, "안녕하세요");
		chatMessageRepository.saveAndFlush(ChatMessage.builder().chatRoom(chatRoom).sender(agent)
			.messageType(MessageType.IMAGE).imageFileKey("chat/1.jpg").build());

		var landlordView = chatService.readRooms(landlord.getUserId()).getRooms().get(0);
		var agentView = chatService.readRooms(agent.getUserId()).getRooms().get(0);

		assertEquals("김중개", landlordView.getPartnerName());
		assertEquals("망원 행복공인중개사사무소", landlordView.getPartnerOfficeName());
		assertEquals("박임대", agentView.getPartnerName());
		assertNull(agentView.getPartnerOfficeName());
		assertEquals("사진", landlordView.getLastMessage());
		assertEquals(ChatRoomType.LANDLORD_AGENT, landlordView.getRoomType());
		assertEquals(property.getPropertyId(), landlordView.getPropertyId());
		assertEquals(NOW, landlordView.getLastMessageAt());
	}

	@Test
	@DisplayName("안 읽은 수는 상대가 보낸 메시지 중 내가 마지막으로 읽은 메시지보다 뒤에 온 것만 센다")
	void countsUnreadMessagesFromPartner() {
		ChatRoom chatRoom = room(property(PropertyStatus.ACTIVE), tenant, ChatRoomType.TENANT_AGENT, NOW);
		ChatMessage read = message(chatRoom, agent, "읽은 메시지");
		message(chatRoom, agent, "안 읽은 메시지 1");
		message(chatRoom, tenant, "내가 보낸 메시지");
		message(chatRoom, agent, "안 읽은 메시지 2");
		chatRepository.saveAndFlush(copyWithLastRead(chatRoom, read.getChatMessageId()));

		var tenantView = chatService.readRooms(tenant.getUserId()).getRooms().get(0);
		var agentView = chatService.readRooms(agent.getUserId()).getRooms().get(0);

		assertEquals(2L, tenantView.getUnreadCount());
		// 중개사는 아직 아무것도 읽지 않아 상대가 보낸 1개가 남는다
		assertEquals(1L, agentView.getUnreadCount());
	}

	@Test
	@DisplayName("읽기 전용 방은 매물이 삭제됐는지 계약됐는지 알려 준다")
	void tellsReadOnlyReason() {
		ChatRoom contracted = chatRepository.saveAndFlush(ChatRoom.builder().property(property(PropertyStatus.CONTRACTED))
			.roomType(ChatRoomType.TENANT_AGENT).inquirer(tenant).agent(agent).chatStatus(ChatStatus.READ_ONLY)
			.lastMessageAt(NOW).build());
		room(property(PropertyStatus.ACTIVE), tenant, ChatRoomType.TENANT_AGENT, NOW.minusDays(1));

		var rooms = chatService.readRooms(tenant.getUserId()).getRooms();

		assertEquals(contracted.getChatRoomId(), rooms.get(0).getChatRoomId());
		assertEquals(ChatStatus.READ_ONLY, rooms.get(0).getChatStatus());
		assertEquals(PropertyStatus.CONTRACTED, rooms.get(0).getReadOnlyReason());
		assertEquals(ChatStatus.ACTIVE, rooms.get(1).getChatStatus());
		assertNull(rooms.get(1).getReadOnlyReason());
	}

	@Test
	@DisplayName("참여한 방이 없으면 빈 목록이다")
	void readsEmptyRooms() {
		assertTrue(chatService.readRooms(tenant.getUserId()).getRooms().isEmpty());
	}

	@Test
	@DisplayName("대화 내역은 최근 30개를 오래된 것부터 주고, cursor로 그보다 오래된 메시지를 이어 받는다")
	void readsMessagesWithCursor() {
		ChatRoom chatRoom = room(property(PropertyStatus.ACTIVE), tenant, ChatRoomType.TENANT_AGENT, NOW);
		List<ChatMessage> sent = IntStream.rangeClosed(1, 35)
			.mapToObj(index -> message(chatRoom, index % 2 == 0 ? agent : tenant, "메시지 " + index))
			.toList();

		var first = chatService.readMessages(tenant.getUserId(), chatRoom.getChatRoomId(), null);
		var second = chatService.readMessages(tenant.getUserId(), chatRoom.getChatRoomId(), first.getNextCursor());

		assertEquals(30, first.getMessages().size());
		assertEquals("메시지 6", first.getMessages().get(0).getContent());
		assertEquals("메시지 35", first.getMessages().get(29).getContent());
		assertTrue(first.getHasNext());
		assertEquals(sent.get(5).getChatMessageId(), first.getNextCursor());
		assertEquals(List.of("메시지 1", "메시지 2", "메시지 3", "메시지 4", "메시지 5"),
			second.getMessages().stream().map(ChatDto.MessageItemResponse::getContent).toList());
		assertFalse(second.getHasNext());
		assertNull(second.getNextCursor());
		assertTrue(second.getMessages().get(0).getIsMine());
		assertFalse(second.getMessages().get(1).getIsMine());
	}

	@Test
	@DisplayName("메시지가 없는 방은 빈 내역이다")
	void readsEmptyMessages() {
		ChatRoom chatRoom = room(property(PropertyStatus.ACTIVE), tenant, ChatRoomType.TENANT_AGENT, null);

		var response = chatService.readMessages(agent.getUserId(), chatRoom.getChatRoomId(), null);

		assertTrue(response.getMessages().isEmpty());
		assertFalse(response.getHasNext());
	}

	@Test
	@DisplayName("참여하지 않은 방이나 없는 방의 대화는 볼 수 없다")
	void rejectsOthersRoom() {
		ChatRoom chatRoom = room(property(PropertyStatus.ACTIVE), tenant, ChatRoomType.TENANT_AGENT, NOW);

		for (Long chatRoomId : new Long[] {chatRoom.getChatRoomId(), chatRoom.getChatRoomId() + 100}) {
			var exception = assertThrows(ChatException.class,
				() -> chatService.readMessages(landlord.getUserId(), chatRoomId, null));

			assertEquals(ErrorCode.CHAT_ROOM_NOT_FOUND, exception.getErrorCode());
		}
	}

	private ChatRoom room(Property property, User inquirer, ChatRoomType roomType, LocalDateTime lastMessageAt) {
		return chatRepository.saveAndFlush(ChatRoom.builder().property(property).roomType(roomType).inquirer(inquirer)
			.agent(agent).lastMessageAt(lastMessageAt).build());
	}

	private ChatRoom copyWithLastRead(ChatRoom chatRoom, Long inquirerLastReadMessageId) {
		return ChatRoom.builder().chatRoomId(chatRoom.getChatRoomId()).property(chatRoom.getProperty())
			.roomType(chatRoom.getRoomType()).inquirer(chatRoom.getInquirer()).agent(chatRoom.getAgent())
			.lastMessageAt(chatRoom.getLastMessageAt()).inquirerLastReadMessageId(inquirerLastReadMessageId)
			.createdAt(chatRoom.getCreatedAt()).build();
	}

	private ChatMessage message(ChatRoom chatRoom, User sender, String content) {
		return chatMessageRepository.saveAndFlush(ChatMessage.builder().chatRoom(chatRoom).sender(sender).content(content).build());
	}

	private Property property(PropertyStatus status) {
		return propertyRepository.saveAndFlush(Property.builder().landlord(landlord).agent(agent)
			.leaseType(LeaseType.JEONSE).deposit(BigDecimal.valueOf(300_000_000)).roadAddress("서울특별시 마포구 월드컵로 61")
			.propertyStatus(status).build());
	}

	private User user(String name, Role role) {
		return userRepository.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash")
			.name(name).phone("010" + (int) (Math.random() * 100_000_000)).role(role).build());
	}
}
