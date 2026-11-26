package com.bogeum.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.bogeum.domain.chat.ChatRepository;
import com.bogeum.domain.user.UserException;
import com.bogeum.domain.user.UserService;
import com.bogeum.utils.JwtUtil;

import io.jsonwebtoken.Claims;

/** STOMP 연결은 JWT로, 구독은 참여한 방과 내 목록 갱신만 받는지 확인한다 (CHAT_003). */
@ExtendWith(MockitoExtension.class)
class StompAuthInterceptorTest {

	@Mock
	private JwtUtil jwtUtil;
	@Mock
	private UserService userService;
	@Mock
	private ChatRepository chatRepository;

	private StompAuthInterceptor interceptor;
	private final MessageChannel channel = mock(MessageChannel.class);
	private final Principal loginUser = UsernamePasswordAuthenticationToken.authenticated("7", null, null);

	@BeforeEach
	void setUp() {
		interceptor = new StompAuthInterceptor(jwtUtil, userService, chatRepository);
	}

	@Test
	@DisplayName("CONNECT의 Bearer 토큰이 맞으면 사용자 번호를 이름으로 둔다")
	void connectsWithToken() {
		Claims claims = mock(Claims.class);
		when(claims.getSubject()).thenReturn("7");
		when(jwtUtil.validateAccessToken("good-token")).thenReturn(claims);
		StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);
		accessor.setNativeHeader("Authorization", "Bearer good-token");

		interceptor.preSend(message(accessor), channel);

		assertEquals("7", accessor.getUser().getName());
		// 탈퇴·정지된 사용자인지 확인한다
		verify(userService).findUser(7L);
	}

	@Test
	@DisplayName("토큰이 없거나 형식이 틀리면 연결할 수 없다")
	void rejectsMissingToken() {
		for (String header : new String[] {null, "good-token", "Basic abc"}) {
			StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);
			if (header != null) {
				accessor.setNativeHeader("Authorization", header);
			}

			assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(accessor), channel));
		}
	}

	@Test
	@DisplayName("만료되거나 잘못된 토큰이면 연결할 수 없다")
	void rejectsInvalidToken() {
		when(jwtUtil.validateAccessToken("bad-token")).thenThrow(UserException.invalidAccess());
		StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);
		accessor.setNativeHeader("Authorization", "Bearer bad-token");

		assertThrows(UserException.class, () -> interceptor.preSend(message(accessor), channel));
	}

	@Test
	@DisplayName("참여한 방의 대화와 내 목록 갱신은 구독할 수 있다")
	void subscribesOwnRoomAndQueue() {
		when(chatRepository.existsParticipant(126L, 7L)).thenReturn(true);

		assertDoesNotThrow(() -> interceptor.preSend(subscribe("/topic/chat-rooms/126", loginUser), channel));
		// 상대가 어디까지 읽었는지 알림 (CHAT_007)
		assertDoesNotThrow(() -> interceptor.preSend(subscribe("/topic/chat-rooms/126/read", loginUser), channel));
		assertDoesNotThrow(() -> interceptor.preSend(subscribe("/user/queue/chat-rooms", loginUser), channel));
		// 내 안 읽은 알림 수 (NOTIFY_007, NOTIFY_021)
		assertDoesNotThrow(() -> interceptor.preSend(subscribe("/user/queue/notifications", loginUser), channel));
	}

	@Test
	@DisplayName("참여하지 않은 방, 정해지지 않은 주소, 로그인 전 구독은 막는다")
	void rejectsOtherSubscriptions() {
		when(chatRepository.existsParticipant(127L, 7L)).thenReturn(false);

		assertThrows(AccessDeniedException.class,
			() -> interceptor.preSend(subscribe("/topic/chat-rooms/127", loginUser), channel));
		assertThrows(AccessDeniedException.class,
			() -> interceptor.preSend(subscribe("/topic/chat-rooms/127/read", loginUser), channel));
		assertThrows(AccessDeniedException.class,
			() -> interceptor.preSend(subscribe("/topic/everything", loginUser), channel));
		assertThrows(AccessDeniedException.class,
			() -> interceptor.preSend(subscribe("/topic/chat-rooms/126", null), channel));
	}

	@Test
	@DisplayName("메시지는 REST로만 받아서 SEND는 막는다")
	void rejectsSend() {
		StompHeaderAccessor accessor = accessor(StompCommand.SEND);
		accessor.setDestination("/app/chat-rooms/126");
		accessor.setUser(loginUser);

		assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(accessor), channel));
	}

	private Message<byte[]> subscribe(String destination, Principal user) {
		StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
		accessor.setDestination(destination);
		accessor.setUser(user);
		return message(accessor);
	}

	private StompHeaderAccessor accessor(StompCommand command) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		// 가로채는 쪽이 사용자 정보를 넣을 수 있게 열어 둔다
		accessor.setLeaveMutable(true);
		return accessor;
	}

	private Message<byte[]> message(StompHeaderAccessor accessor) {
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}
}
