package com.bogeum.domain.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.common.enums.ChatStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.MessageType;
import com.bogeum.common.enums.NotificationRefType;
import com.bogeum.common.enums.NotificationType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.chat.ChatMessageRepository;
import com.bogeum.domain.chat.ChatRepository;
import com.bogeum.domain.chat.ChatService;
import com.bogeum.domain.chat.model.ChatRoom;
import com.bogeum.domain.notification.model.Notification;
import com.bogeum.domain.notification.model.NotificationDto;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

/**
 * 메시지 저장이 확정되면 방을 보고 있지 않은 상대에게만 새 메시지 알림이 생기는지 H2로 확인한다 (CHAT_008, NOTIFY_023).
 * 저장 확정 뒤에 도는 리스너까지 보려고 테스트 트랜잭션 없이 실행하고 끝나면 지운다
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-chat-notification-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ ChatService.class, NotificationService.class, NotificationEventListener.class,
	NotificationPublisher.class })
class ChatMessageNotificationCreateTest {

	@Autowired
	private ChatService chatService;
	@Autowired
	private NotificationService notificationService;
	@Autowired
	private NotificationRepository notificationRepository;
	@Autowired
	private ChatRepository chatRepository;
	@Autowired
	private ChatMessageRepository chatMessageRepository;
	@Autowired
	private PropertyRepository propertyRepository;
	@Autowired
	private UserRepository userRepository;
	@MockitoBean
	private SimpUserRegistry simpUserRegistry;
	@MockitoBean
	private SimpMessagingTemplate messagingTemplate;
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

	@AfterEach
	void tearDown() {
		notificationRepository.deleteAll();
		chatMessageRepository.deleteAll();
		chatRepository.deleteAll();
		propertyRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	@DisplayName("메시지를 보내면 보낸 사람이 아닌 상대에게만 새 메시지 알림이 생긴다")
	void createsNotificationForPartner() {
		chatService.createMessage(tenant.getUserId(), chatRoom.getChatRoomId(), "  매물 아직 있나요?  ");

		List<Notification> notifications = notificationRepository.findAll();
		assertEquals(1, notifications.size());
		Notification notification = notifications.get(0);
		assertEquals(agent.getUserId(), notification.getUser().getUserId());
		assertEquals(NotificationType.CHAT_MESSAGE, notification.getType());
		assertEquals("새 메시지", notification.getTitle());
		assertEquals("최세입: 매물 아직 있나요?", notification.getBody());
		assertEquals(NotificationRefType.CHAT_ROOM, notification.getRefType());
		assertEquals(chatRoom.getChatRoomId(), notification.getRefId());
		assertNull(notification.getReadAt());
	}

	@Test
	@DisplayName("상대가 그 방을 보고 있으면 알림을 만들지 않고, 다른 방을 보고 있으면 만든다")
	void skipsWhenPartnerIsViewingRoom() {
		viewing(agent, "/topic/chat-rooms/" + chatRoom.getChatRoomId());
		chatService.createMessage(tenant.getUserId(), chatRoom.getChatRoomId(), "안녕하세요");
		assertEquals(0, notificationRepository.count());

		viewing(agent, "/topic/chat-rooms/" + (chatRoom.getChatRoomId() + 100));
		chatService.createMessage(tenant.getUserId(), chatRoom.getChatRoomId(), "안녕하세요");
		assertEquals(1, notificationRepository.count());
	}

	@Test
	@DisplayName("같은 방에서 연달아 오면 안 읽은 알림은 최신 메시지 하나만 남는다")
	void keepsOneUnreadNotificationPerRoom() {
		chatService.createMessage(agent.getUserId(), chatRoom.getChatRoomId(), "안녕하세요");
		chatService.createMessage(agent.getUserId(), chatRoom.getChatRoomId(), "방문 가능하세요?");

		List<Notification> notifications = notificationRepository.findAll();
		assertEquals(1, notifications.size());
		assertEquals(tenant.getUserId(), notifications.get(0).getUser().getUserId());
		assertEquals("김중개: 방문 가능하세요?", notifications.get(0).getBody());
	}

	@Test
	@DisplayName("채팅방에서 메시지를 읽으면 그 방의 새 메시지 알림도 읽음이 된다")
	void readsNotificationWithRoom() {
		chatService.createMessage(agent.getUserId(), chatRoom.getChatRoomId(), "안녕하세요");

		chatService.updateRead(tenant.getUserId(), chatRoom.getChatRoomId());

		assertNotNull(notificationRepository.findAll().get(0).getReadAt());
		// 읽은 알림은 남겨 두고, 다음 메시지는 새 안 읽은 알림으로 만든다
		chatService.createMessage(agent.getUserId(), chatRoom.getChatRoomId(), "답장 기다릴게요");
		assertEquals(2, notificationRepository.count());
	}

	@Test
	@DisplayName("사진은 '사진을 보냈어요.', 긴 메시지는 100자에서 잘라 보여 준다")
	void buildsPreview() {
		notificationService.createChatMessageNotification(tenant.getUserId(), agent.getUserId(), chatRoom.getChatRoomId(),
			MessageType.IMAGE, null);
		assertEquals("김중개: 사진을 보냈어요.", notificationRepository.findAll().get(0).getBody());

		notificationService.createChatMessageNotification(tenant.getUserId(), agent.getUserId(), chatRoom.getChatRoomId(),
			MessageType.TEXT, "가".repeat(150));
		String body = notificationRepository.findAll().get(0).getBody();
		assertEquals("김중개: " + "가".repeat(100) + "…", body);
		assertTrue(body.length() < 500);
	}

	@Test
	@DisplayName("새 메시지 알림이 생기거나 방에서 읽으면, 확정된 뒤 받는 사람에게 바뀐 안 읽은 알림 수를 보낸다")
	void publishesUnreadCount() {
		chatService.createMessage(tenant.getUserId(), chatRoom.getChatRoomId(), "안녕하세요");
		verify(messagingTemplate).convertAndSendToUser(eq(String.valueOf(agent.getUserId())), eq("/queue/notifications"),
			argThat(payload -> ((NotificationDto.UnreadCountResponse) payload).getUnreadCount() == 1L));

		chatService.updateRead(agent.getUserId(), chatRoom.getChatRoomId());
		verify(messagingTemplate).convertAndSendToUser(eq(String.valueOf(agent.getUserId())), eq("/queue/notifications"),
			argThat(payload -> ((NotificationDto.UnreadCountResponse) payload).getUnreadCount() == 0L));
	}

	private void viewing(User user, String destination) {
		SimpUser simpUser = mock(SimpUser.class);
		SimpSession session = mock(SimpSession.class);
		SimpSubscription subscription = mock(SimpSubscription.class);
		when(subscription.getDestination()).thenReturn(destination);
		when(session.getSubscriptions()).thenReturn(Set.of(subscription));
		when(simpUser.getSessions()).thenReturn(Set.of(session));
		when(simpUserRegistry.getUser(String.valueOf(user.getUserId()))).thenReturn(simpUser);
	}

	private User user(String name) {
		return userRepository.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash")
			.name(name).phone("010" + (int) (Math.random() * 100_000_000)).role(Role.USER).build());
	}
}
