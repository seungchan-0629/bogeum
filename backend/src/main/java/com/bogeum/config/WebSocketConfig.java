package com.bogeum.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * 채팅 실시간 전달 (CHAT_003). 메시지 저장은 REST로 받고, 저장된 메시지를 STOMP 구독자에게 뿌린다.
 * - 연결: /ws (인증은 STOMP CONNECT 헤더의 JWT로, StompAuthInterceptor)
 * - 방 대화: /topic/chat-rooms/{chatRoomId}
 * - 내 채팅 목록 갱신: /user/queue/chat-rooms
 * 서버 한 대 안에서만 중계하는 기본 중계를 쓴다. 서버를 늘리면 외부 중계(Redis 등)로 바꾼다
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final StompAuthInterceptor stompAuthInterceptor;

	// dev·prod 설정 파일에서 받는다. 설정이 없는 프로필(test·demo)은 빈 목록이라 다른 출처의 연결을 모두 막는다
	@Value("${chat.allowed-origins:}")
	private String[] allowedOrigins;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic", "/queue");
		registry.setUserDestinationPrefix("/user");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(stompAuthInterceptor);
	}
}
