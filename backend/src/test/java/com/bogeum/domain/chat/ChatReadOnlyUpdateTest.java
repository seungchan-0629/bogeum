package com.bogeum.domain.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import com.bogeum.domain.chat.model.ChatRoom;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;

/**
 * 매물이 삭제되면 방이 모두, 계약되면 계약 당사자가 아닌 문의 방만 읽기 전용이 되는지 H2로 확인한다 (CHAT_009).
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-chat-read-only-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChatService.class)
@RecordApplicationEvents
class ChatReadOnlyUpdateTest {

	@Autowired
	private ChatService chatService;
	@Autowired
	private ChatRepository chatRepository;
	@Autowired
	private PropertyRepository propertyRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ApplicationEvents applicationEvents;
	// 채팅방 열기에만 쓰는 의존성
	@MockitoBean
	private UserService userService;

	private User landlord;
	private User agent;
	private User contractedTenant;
	private User otherTenant;
	private Property property;
	private ChatRoom contractedRoom;
	private ChatRoom otherRoom;
	private ChatRoom landlordRoom;

	@BeforeEach
	void setUp() {
		landlord = user("박임대");
		agent = user("김중개");
		contractedTenant = user("최세입");
		otherTenant = user("오세입");
		property = propertyRepository.saveAndFlush(Property.builder().landlord(landlord).agent(agent)
			.leaseType(LeaseType.JEONSE).deposit(BigDecimal.valueOf(300_000_000)).roadAddress("서울특별시 마포구 월드컵로 61")
			.propertyStatus(PropertyStatus.ACTIVE).build());
		contractedRoom = chatRepository.saveAndFlush(ChatRoom.createInquiryRoom(property, contractedTenant));
		otherRoom = chatRepository.saveAndFlush(ChatRoom.createInquiryRoom(property, otherTenant));
		landlordRoom = chatRepository.saveAndFlush(ChatRoom.createBrokerageRoom(property));
	}

	@Test
	@DisplayName("매물을 삭제하면 그 매물의 방이 모두 읽기 전용이 되고 참여자들에게 알릴 이벤트가 나간다")
	void changesAllRoomsOnDelete() {
		chatService.updateReadOnlyByDelete(property.getPropertyId());

		assertEquals(List.of(ChatStatus.READ_ONLY, ChatStatus.READ_ONLY, ChatStatus.READ_ONLY), statuses());
		ChatRoomsReadOnlyEvent event = applicationEvents.stream(ChatRoomsReadOnlyEvent.class).findFirst().orElseThrow();
		assertEquals(List.of(
			new ChatRoomsReadOnlyEvent.Room(contractedRoom.getChatRoomId(), List.of(contractedTenant.getUserId(), agent.getUserId())),
			new ChatRoomsReadOnlyEvent.Room(otherRoom.getChatRoomId(), List.of(otherTenant.getUserId(), agent.getUserId())),
			new ChatRoomsReadOnlyEvent.Room(landlordRoom.getChatRoomId(), List.of(landlord.getUserId(), agent.getUserId()))),
			event.rooms());
	}

	@Test
	@DisplayName("계약하면 계약한 임차인 방과 임대인 방은 그대로 쓰고, 다른 문의자 방만 읽기 전용이 된다")
	void keepsContractPartiesRoomsOnContract() {
		chatService.updateReadOnlyByContract(property.getPropertyId(), contractedTenant.getUserId());

		assertEquals(List.of(ChatStatus.ACTIVE, ChatStatus.READ_ONLY, ChatStatus.ACTIVE), statuses());
		ChatRoomsReadOnlyEvent event = applicationEvents.stream(ChatRoomsReadOnlyEvent.class).findFirst().orElseThrow();
		assertEquals(List.of(new ChatRoomsReadOnlyEvent.Room(otherRoom.getChatRoomId(),
			List.of(otherTenant.getUserId(), agent.getUserId()))), event.rooms());
	}

	@Test
	@DisplayName("이미 읽기 전용인 방은 다시 바꾸지 않고, 바뀐 방이 없으면 알리지 않는다")
	void skipsAlreadyReadOnlyRooms() {
		chatService.updateReadOnlyByContract(property.getPropertyId(), contractedTenant.getUserId());
		chatService.updateReadOnlyByContract(property.getPropertyId(), contractedTenant.getUserId());

		assertEquals(1L, applicationEvents.stream(ChatRoomsReadOnlyEvent.class).count());
	}

	@Test
	@DisplayName("읽기 전용이 된 방은 목록에서 계약 체결 이유와 함께 보인다")
	void showsReadOnlyReasonInRoomList() {
		property.contract();
		chatService.updateReadOnlyByContract(property.getPropertyId(), contractedTenant.getUserId());

		var room = chatService.readRooms(otherTenant.getUserId()).getRooms().get(0);

		assertEquals(ChatStatus.READ_ONLY, room.getChatStatus());
		assertEquals(PropertyStatus.CONTRACTED, room.getReadOnlyReason());
	}

	private List<ChatStatus> statuses() {
		return List.of(contractedRoom, otherRoom, landlordRoom).stream()
			.map(chatRoom -> chatRepository.findById(chatRoom.getChatRoomId()).orElseThrow().getChatStatus())
			.toList();
	}

	private User user(String name) {
		return userRepository.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash")
			.name(name).phone("010" + (int) (Math.random() * 100_000_000)).role(Role.USER).build());
	}
}
