package com.bogeum.domain.chat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.bogeum.common.enums.ChatBlockStatus;
import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.common.enums.ChatStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.chat.model.ChatBlock;
import com.bogeum.domain.chat.model.ChatRoom;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

/**
 * 채팅방에서 상대를 차단하면 두 사람이 함께 있는 모든 방에서 서로 보낼 수 없고, 풀면 다시 보낼 수 있는지 H2로 확인한다 (CHAT_010).
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-chat-block-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChatService.class)
@RecordApplicationEvents
class ChatBlockTest {

	@Autowired
	private ChatService chatService;
	@Autowired
	private ChatRepository chatRepository;
	@Autowired
	private ChatBlockRepository chatBlockRepository;
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
	@DisplayName("차단하면 내가 상대를 차단한 기록이 남고 두 사람에게 알릴 이벤트가 나간다. 다시 눌러도 한 번만 남는다")
	void blocksPartnerOnce() {
		chatService.createBlock(tenant.getUserId(), chatRoom.getChatRoomId());
		chatService.createBlock(tenant.getUserId(), chatRoom.getChatRoomId());

		List<ChatBlock> blocks = chatBlockRepository.findAll();
		assertEquals(1, blocks.size());
		assertEquals(tenant.getUserId(), blocks.get(0).getBlocker().getUserId());
		assertEquals(agent.getUserId(), blocks.get(0).getBlocked().getUserId());
		assertEquals(List.of(new ChatBlockChangedEvent(chatRoom.getChatRoomId(), List.of(tenant.getUserId(), agent.getUserId()))),
			applicationEvents.stream(ChatBlockChangedEvent.class).toList());
	}

	@Test
	@DisplayName("차단하면 두 사람 모두 글과 사진을 보낼 수 없고, 같은 두 사람의 다른 매물 방도 마찬가지다")
	void blocksSendingBothWays() {
		ChatRoom otherRoom = room(ChatStatus.ACTIVE);
		chatService.createBlock(tenant.getUserId(), chatRoom.getChatRoomId());

		for (Long userId : new Long[] {tenant.getUserId(), agent.getUserId()}) {
			for (Long chatRoomId : new Long[] {chatRoom.getChatRoomId(), otherRoom.getChatRoomId()}) {
				assertErrorCode(ErrorCode.CHAT_BLOCKED, () -> chatService.createMessage(userId, chatRoomId, "안녕하세요"));
			}
		}
		assertErrorCode(ErrorCode.CHAT_BLOCKED, () -> chatService.createImageMessage(agent.getUserId(), chatRoom.getChatRoomId(),
			new MockMultipartFile("image", "a.png", "image/png", new byte[] {1})));
	}

	@Test
	@DisplayName("내가 건 차단을 풀면 다시 보낼 수 있고, 차단한 적이 없으면 그대로 끝난다")
	void unblocks() {
		chatService.createBlock(tenant.getUserId(), chatRoom.getChatRoomId());

		chatService.deleteBlock(tenant.getUserId(), chatRoom.getChatRoomId());
		chatService.deleteBlock(tenant.getUserId(), chatRoom.getChatRoomId());

		assertEquals(0L, chatBlockRepository.count());
		assertDoesNotThrow(() -> chatService.createMessage(agent.getUserId(), chatRoom.getChatRoomId(), "다시 연락드려요"));
		// 차단 1번, 해제 1번만 알린다
		assertEquals(2L, applicationEvents.stream(ChatBlockChangedEvent.class).count());
	}

	@Test
	@DisplayName("상대가 나를 차단한 것은 내가 풀 수 없다")
	void cannotUnblockPartnersBlock() {
		chatService.createBlock(agent.getUserId(), chatRoom.getChatRoomId());

		chatService.deleteBlock(tenant.getUserId(), chatRoom.getChatRoomId());

		assertEquals(1L, chatBlockRepository.count());
		assertErrorCode(ErrorCode.CHAT_BLOCKED, () -> chatService.createMessage(tenant.getUserId(), chatRoom.getChatRoomId(), "안녕하세요"));
	}

	@Test
	@DisplayName("채팅 목록에는 내가 차단했는지, 상대가 나를 차단했는지가 나온다")
	void showsBlockStatusInRoomList() {
		User otherTenant = user("오세입");
		chatRepository.saveAndFlush(ChatRoom.createInquiryRoom(chatRoom.getProperty(), otherTenant));
		chatService.createBlock(tenant.getUserId(), chatRoom.getChatRoomId());

		assertEquals(ChatBlockStatus.BLOCKED_BY_ME, chatService.readRooms(tenant.getUserId()).getRooms().get(0).getBlockStatus());
		var agentRooms = chatService.readRooms(agent.getUserId()).getRooms();
		assertEquals(ChatBlockStatus.BLOCKED_ME, agentRooms.stream()
			.filter(room -> room.getChatRoomId().equals(chatRoom.getChatRoomId())).findFirst().orElseThrow().getBlockStatus());
		// 차단과 관계없는 다른 문의자와의 방은 그대로다
		assertEquals(ChatBlockStatus.NONE, agentRooms.stream()
			.filter(room -> !room.getChatRoomId().equals(chatRoom.getChatRoomId())).findFirst().orElseThrow().getBlockStatus());
	}

	@Test
	@DisplayName("읽기 전용이면서 차단된 방은 읽기 전용 이유를 먼저 알린다")
	void readOnlyComesBeforeBlock() {
		ChatRoom readOnly = room(ChatStatus.READ_ONLY);
		chatService.createBlock(tenant.getUserId(), readOnly.getChatRoomId());

		assertErrorCode(ErrorCode.CHAT_ROOM_READ_ONLY, () -> chatService.createMessage(tenant.getUserId(), readOnly.getChatRoomId(), "안녕하세요"));
	}

	@Test
	@DisplayName("참여하지 않은 방에서는 차단하거나 풀 수 없다")
	void rejectsOthersRoom() {
		User stranger = user("오세입");

		assertErrorCode(ErrorCode.CHAT_ROOM_NOT_FOUND, () -> chatService.createBlock(stranger.getUserId(), chatRoom.getChatRoomId()));
		assertErrorCode(ErrorCode.CHAT_ROOM_NOT_FOUND, () -> chatService.deleteBlock(stranger.getUserId(), chatRoom.getChatRoomId()));
		assertEquals(0L, chatBlockRepository.count());
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
