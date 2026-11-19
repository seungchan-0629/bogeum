package com.bogeum.config;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import com.bogeum.domain.chat.ChatRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.utils.JwtUtil;

import lombok.RequiredArgsConstructor;

/**
 * STOMP 연결과 구독을 확인한다 (CHAT_003).
 * 브라우저 WebSocket은 연결할 때 헤더를 넣을 수 없어서, /ws 연결은 열어 두고 CONNECT 프레임의 JWT로 사용자를 확인한다.
 * 메시지 보내기는 REST로만 받으므로 SEND는 막는다
 */
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

	private static final Pattern CHAT_ROOM_TOPIC = Pattern.compile("^/topic/chat-rooms/(\\d+)(/read)?$");
	private static final String MY_CHAT_ROOMS_QUEUE = "/user/queue/chat-rooms";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtUtil jwtUtil;
	private final UserService userService;
	private final ChatRepository chatRepository;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() == null) {
			return message;
		}
		switch (accessor.getCommand()) {
			case CONNECT -> accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
			case SUBSCRIBE -> checkSubscription(accessor.getUser(), accessor.getDestination());
			case SEND -> throw new AccessDeniedException("메시지는 REST로 보냅니다.");
			default -> {
			}
		}
		return message;
	}

	// 이름을 사용자 번호로 둬서 /user/{번호}/queue/... 로 내 목록 갱신을 보낼 수 있게 한다
	private Principal authenticate(String header) {
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			throw new AccessDeniedException("로그인이 필요합니다.");
		}
		Long userId = Long.valueOf(jwtUtil.validateAccessToken(header.substring(BEARER_PREFIX.length())).getSubject());
		// 탈퇴·정지된 사용자는 여기서 막힌다
		userService.findUser(userId);
		return UsernamePasswordAuthenticationToken.authenticated(String.valueOf(userId), null, null);
	}

	// 내 목록 갱신과, 내가 참여한 방의 대화·읽음 알림(/read, CHAT_007)만 구독할 수 있다
	private void checkSubscription(Principal user, String destination) {
		if (user == null || destination == null) {
			throw new AccessDeniedException("로그인이 필요합니다.");
		}
		if (MY_CHAT_ROOMS_QUEUE.equals(destination)) {
			return;
		}
		Matcher matcher = CHAT_ROOM_TOPIC.matcher(destination);
		if (!matcher.matches() || !chatRepository.existsParticipant(Long.valueOf(matcher.group(1)), Long.valueOf(user.getName()))) {
			throw new AccessDeniedException("구독할 수 없는 주소입니다.");
		}
	}
}
