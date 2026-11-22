package com.bogeum.domain.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.common.enums.ChatStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.MessageType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.common.exception.ImageException;
import com.bogeum.domain.chat.model.ChatMessage;
import com.bogeum.domain.chat.model.ChatRoom;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

/**
 * 사진 한 장이 검사·변환을 거쳐 채팅 저장소에 올라가고 사진 메시지로 남는지 H2로 확인한다 (CHAT_004).
 * 저장소와 사진 검사기는 가짜로 둔다.
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-chat-image-create-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChatService.class)
@RecordApplicationEvents
class ChatImageMessageCreateTest {

	private static final byte[] JPEG = {1, 2, 3};

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
	@MockitoBean
	private ChatImageStorage storage;
	@MockitoBean
	private ImageProcessor imageProcessor;
	// 채팅방 열기에만 쓰는 의존성
	@MockitoBean
	private UserService userService;

	private User tenant;
	private User agent;
	private ChatRoom chatRoom;

	@BeforeEach
	void setUp() {
		tenant = user("최세입");
		agent = user("김중개");
		chatRoom = room(ChatStatus.ACTIVE);
		when(imageProcessor.process(any())).thenReturn(JPEG);
		when(storage.createReadUrl(anyString())).thenAnswer(invocation -> "https://s3.example.com/" + invocation.getArgument(0));
	}

	@Test
	@DisplayName("사진을 보내면 JPEG로 바꿔 방별 키로 올리고, 사진 메시지와 조회 주소를 남긴다")
	void savesImageMessage() {
		var response = chatService.createImageMessage(tenant.getUserId(), chatRoom.getChatRoomId(), photo());

		var key = ArgumentCaptor.forClass(String.class);
		verify(storage).write(key.capture(), eq(JPEG));
		assertTrue(key.getValue().matches("chat-images/" + chatRoom.getChatRoomId() + "/[0-9a-f-]{36}\\.jpg"));
		ChatMessage saved = chatMessageRepository.findById(response.getChatMessageId()).orElseThrow();
		assertEquals(MessageType.IMAGE, saved.getMessageType());
		assertEquals(key.getValue(), saved.getImageFileKey());
		assertNull(saved.getContent());
		assertEquals(saved.getCreatedAt(), chatRepository.findById(chatRoom.getChatRoomId()).orElseThrow().getLastMessageAt());
		assertEquals("https://s3.example.com/" + key.getValue(), response.getImageUrl());
		assertTrue(response.getIsMine());
	}

	@Test
	@DisplayName("방 구독자에게 뿌릴 메시지에도 사진 주소가 담긴다")
	void publishesImageUrl() {
		var response = chatService.createImageMessage(agent.getUserId(), chatRoom.getChatRoomId(), photo());

		ChatMessageCreatedEvent event = applicationEvents.stream(ChatMessageCreatedEvent.class).findFirst().orElseThrow();
		assertEquals(MessageType.IMAGE, event.message().getMessageType());
		assertEquals(response.getImageUrl(), event.message().getImageUrl());
		assertNull(event.message().getIsMine());
	}

	@Test
	@DisplayName("대화 내역은 사진 메시지에만 새 조회 주소를 붙인다")
	void readsImageUrlInHistory() {
		chatMessageRepository.saveAndFlush(ChatMessage.createText(chatRoom, tenant, "사진 보내드려요"));
		chatMessageRepository.saveAndFlush(ChatMessage.createImage(chatRoom, tenant, "chat-images/1/a.jpg"));

		var messages = chatService.readMessages(agent.getUserId(), chatRoom.getChatRoomId(), null).getMessages();

		assertNull(messages.get(0).getImageUrl());
		assertEquals("https://s3.example.com/chat-images/1/a.jpg", messages.get(1).getImageUrl());
	}

	@Test
	@DisplayName("참여하지 않은 방이나 읽기 전용 방에는 사진을 보낼 수 없고 저장소도 쓰지 않는다")
	void rejectsOthersOrReadOnlyRoom() {
		var notFound = assertThrows(ChatException.class,
			() -> chatService.createImageMessage(user("오세입").getUserId(), chatRoom.getChatRoomId(), photo()));
		var readOnly = assertThrows(ChatException.class,
			() -> chatService.createImageMessage(tenant.getUserId(), room(ChatStatus.READ_ONLY).getChatRoomId(), photo()));

		assertEquals(ErrorCode.CHAT_ROOM_NOT_FOUND, notFound.getErrorCode());
		assertEquals(ErrorCode.CHAT_ROOM_READ_ONLY, readOnly.getErrorCode());
		verifyNoInteractions(storage, imageProcessor);
	}

	@Test
	@DisplayName("사진이 없거나 검사에서 거절되면 올리지 않는다")
	void rejectsMissingOrInvalidImage() {
		assertThrows(ImageException.class, () -> chatService.createImageMessage(tenant.getUserId(), chatRoom.getChatRoomId(), null));
		assertThrows(ImageException.class, () -> chatService.createImageMessage(tenant.getUserId(), chatRoom.getChatRoomId(),
			new MockMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0])));
		when(imageProcessor.process(any())).thenThrow(ImageException.invalidImage());
		assertThrows(ImageException.class, () -> chatService.createImageMessage(tenant.getUserId(), chatRoom.getChatRoomId(), photo()));

		verify(storage, never()).write(anyString(), any());
		assertEquals(0L, chatMessageRepository.count());
	}

	@Test
	@DisplayName("저장소에 쓰지 못하면 사진을 저장할 수 없다는 503이다")
	void failsWhenStorageWriteFails() {
		doThrow(new IllegalStateException("S3 down")).when(storage).write(anyString(), any());

		var exception = assertThrows(ChatException.class,
			() -> chatService.createImageMessage(tenant.getUserId(), chatRoom.getChatRoomId(), photo()));

		assertEquals(ErrorCode.CHAT_STORAGE_UNAVAILABLE, exception.getErrorCode());
	}

	@Test
	@DisplayName("메시지 저장이 롤백되면 올린 사진을 지운다")
	void deletesUploadedImageOnRollback() {
		chatService.createImageMessage(tenant.getUserId(), chatRoom.getChatRoomId(), photo());
		var key = ArgumentCaptor.forClass(String.class);
		verify(storage).write(key.capture(), any());

		TransactionSynchronizationManager.getSynchronizations()
			.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

		verify(storage).delete(key.getValue());
	}

	private MockMultipartFile photo() {
		return new MockMultipartFile("image", "defect.png", "image/png", new byte[] {9, 9, 9});
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
