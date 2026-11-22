package com.bogeum.domain.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

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

import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.chat.model.ChatRoom;
import com.bogeum.domain.property.PropertyException;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

/**
 * 누른 사람과 매물의 관계에 따라 맞는 채팅방이 열리고, 같은 방은 하나만 생기는지 H2로 확인한다 (CHAT_002).
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-chat-room-create-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChatService.class)
class ChatRoomCreateTest {

	@Autowired
	private ChatService chatService;
	@Autowired
	private ChatRepository chatRepository;
	@Autowired
	private PropertyRepository propertyRepository;
	@Autowired
	private UserRepository userRepository;
	// 로그인한 사용자를 찾는 부분만 대신한다
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
	}

	@Test
	@DisplayName("임차인이 공개 중인 매물에 문의하면 담당 중개사와의 방이 생기고, 다시 눌러도 같은 방이다")
	void tenantOpensInquiryRoomOnce() {
		Property property = property(PropertyStatus.ACTIVE, agent);

		Long first = chatService.createRoom(tenant.getUserId(), property.getPropertyId()).getChatRoomId();
		Long second = chatService.createRoom(tenant.getUserId(), property.getPropertyId()).getChatRoomId();

		assertEquals(first, second);
		ChatRoom chatRoom = chatRepository.findById(first).orElseThrow();
		assertEquals(ChatRoomType.TENANT_AGENT, chatRoom.getRoomType());
		assertEquals(tenant.getUserId(), chatRoom.getInquirer().getUserId());
		assertEquals(agent.getUserId(), chatRoom.getAgent().getUserId());
		assertEquals(1L, chatRepository.count());
	}

	@Test
	@DisplayName("임차인은 공개 중이 아닌 매물에 문의할 수 없다")
	void tenantCannotOpenRoomForUnpublishedProperty() {
		for (PropertyStatus status : new PropertyStatus[] {PropertyStatus.CONTRACTED, PropertyStatus.DELETED}) {
			Property property = property(status, agent);

			var exception = assertThrows(PropertyException.class,
				() -> chatService.createRoom(tenant.getUserId(), property.getPropertyId()));

			assertEquals(ErrorCode.PROPERTY_NOT_FOUND, exception.getErrorCode());
		}
		assertEquals(0L, chatRepository.count());
	}

	@Test
	@DisplayName("중개 수락 때 만든 방을 임대인과 중개사가 버튼으로 열면 둘 다 그 방이다")
	void landlordAndAgentOpenBrokerageRoom() {
		Property property = property(PropertyStatus.REQUESTED, agent);
		chatService.createBrokerageRoom(property);
		Long brokerageRoomId = chatRepository.findAll().get(0).getChatRoomId();

		Long landlordRoomId = chatService.createRoom(landlord.getUserId(), property.getPropertyId()).getChatRoomId();
		Long agentRoomId = chatService.createRoom(agent.getUserId(), property.getPropertyId()).getChatRoomId();

		assertEquals(brokerageRoomId, landlordRoomId);
		assertEquals(brokerageRoomId, agentRoomId);
		ChatRoom chatRoom = chatRepository.findById(brokerageRoomId).orElseThrow();
		assertEquals(ChatRoomType.LANDLORD_AGENT, chatRoom.getRoomType());
		assertEquals(landlord.getUserId(), chatRoom.getInquirer().getUserId());
		assertEquals(1L, chatRepository.count());
	}

	@Test
	@DisplayName("중개 수락 때 방은 한 번만 만들어진다")
	void createsBrokerageRoomOnce() {
		Property property = property(PropertyStatus.REQUESTED, agent);

		chatService.createBrokerageRoom(property);
		chatService.createBrokerageRoom(property);

		assertEquals(1L, chatRepository.count());
	}

	@Test
	@DisplayName("수락은 됐는데 방이 없는 예전 매물은 중개사가 누를 때 방을 만든다")
	void agentCreatesMissingBrokerageRoom() {
		Property property = property(PropertyStatus.ACTIVE, agent);

		Long chatRoomId = chatService.createRoom(agent.getUserId(), property.getPropertyId()).getChatRoomId();

		ChatRoom chatRoom = chatRepository.findById(chatRoomId).orElseThrow();
		assertEquals(ChatRoomType.LANDLORD_AGENT, chatRoom.getRoomType());
		assertEquals(landlord.getUserId(), chatRoom.getInquirer().getUserId());
	}

	@Test
	@DisplayName("중개사가 수락하기 전이면 임대인은 대화할 상대가 없어 409다")
	void landlordCannotOpenRoomBeforeAccept() {
		Property property = property(PropertyStatus.REQUESTED, null);

		var exception = assertThrows(ChatException.class,
			() -> chatService.createRoom(landlord.getUserId(), property.getPropertyId()));

		assertEquals(ErrorCode.CHAT_AGENT_NOT_ASSIGNED, exception.getErrorCode());
	}

	@Test
	@DisplayName("중개사는 맡지 않은 매물이나 삭제된 매물로 방을 열 수 없다")
	void agentCannotOpenRoomForOthersOrDeletedProperty() {
		Property others = property(PropertyStatus.ACTIVE, user("이중개", Role.AGENT));
		Property deleted = property(PropertyStatus.DELETED, agent);

		for (Property property : new Property[] {others, deleted}) {
			var exception = assertThrows(PropertyException.class,
				() -> chatService.createRoom(agent.getUserId(), property.getPropertyId()));

			assertEquals(ErrorCode.PROPERTY_NOT_FOUND, exception.getErrorCode());
		}
		assertEquals(0L, chatRepository.count());
	}

	@Test
	@DisplayName("임대인도 삭제한 매물로는 방을 열 수 없다")
	void landlordCannotOpenRoomForDeletedProperty() {
		Property property = property(PropertyStatus.DELETED, agent);

		var exception = assertThrows(PropertyException.class,
			() -> chatService.createRoom(landlord.getUserId(), property.getPropertyId()));

		assertEquals(ErrorCode.PROPERTY_NOT_FOUND, exception.getErrorCode());
	}

	private Property property(PropertyStatus status, User assignedAgent) {
		return propertyRepository.saveAndFlush(Property.builder().landlord(landlord).agent(assignedAgent)
			.leaseType(LeaseType.JEONSE).deposit(BigDecimal.valueOf(300_000_000)).roadAddress("서울특별시 마포구 월드컵로 61")
			.propertyStatus(status).build());
	}

	private User user(String name, Role role) {
		User user = userRepository.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash")
			.name(name).phone("010" + (int) (Math.random() * 100_000_000)).role(role).build());
		when(userService.findUser(user.getUserId())).thenReturn(user);
		return user;
	}
}
