package com.bogeum.domain.notification;

import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.bogeum.domain.chat.ChatMessageCreatedEvent;
import com.bogeum.domain.chat.ChatMessageReadEvent;
import com.bogeum.domain.property.BrokerageRequestCreatedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 다른 기능에서 생긴 일을 받아 알림을 만든다.
 * 원래 일(메시지 저장 등)이 확정된 뒤에 처리하고, 알림이 실패해도 원래 일은 그대로 두고 로그만 남긴다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

	private final NotificationService notificationService;
	private final SimpUserRegistry simpUserRegistry;

	// 상대가 그 방 화면을 열어 두고 있으면(방 구독 중) 바로 보이므로 알림을 만들지 않는다 (CHAT_008, NOTIFY_023)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void createChatMessageNotification(ChatMessageCreatedEvent event) {
		Long senderId = event.message().getSenderId();
		event.participantIds().stream()
			.filter(userId -> !userId.equals(senderId) && !isViewingRoom(userId, event.chatRoomId()))
			.forEach(recipientId -> {
				try {
					notificationService.createChatMessageNotification(recipientId, senderId, event.chatRoomId(),
						event.message().getMessageType(), event.message().getContent());
				} catch (RuntimeException exception) {
					log.error("채팅 알림 생성 실패: chatRoomId={}, recipientId={}", event.chatRoomId(), recipientId, exception);
				}
			});
	}

	// 채팅방에서 메시지를 읽으면 그 방의 새 메시지 알림도 읽음 처리한다 (CHAT_008)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void updateChatMessageRead(ChatMessageReadEvent event) {
		try {
			notificationService.updateChatMessageRead(event.userId(), event.chatRoomId());
		} catch (RuntimeException exception) {
			log.error("채팅 알림 읽음 처리 실패: chatRoomId={}, userId={}", event.chatRoomId(), event.userId(), exception);
		}
	}

	// 중개 신청이 확정되면 배정된 중개사에게 알린다 (NOTIFY_022)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void createBrokerageRequestNotification(BrokerageRequestCreatedEvent event) {
		try {
			notificationService.createBrokerageRequestNotification(event.agentUserId(), event.brokerageRequestId(),
				event.landlordName(), event.roadAddress());
		} catch (RuntimeException exception) {
			log.error("중개 신청 알림 생성 실패: brokerageRequestId={}, agentUserId={}", event.brokerageRequestId(),
				event.agentUserId(), exception);
		}
	}

	// STOMP 연결의 사용자 이름은 회원 번호다 (StompAuthInterceptor)
	private boolean isViewingRoom(Long userId, Long chatRoomId) {
		SimpUser user = simpUserRegistry.getUser(String.valueOf(userId));
		String roomDestination = "/topic/chat-rooms/" + chatRoomId;
		return user != null && user.getSessions().stream()
			.flatMap(session -> session.getSubscriptions().stream())
			.anyMatch(subscription -> roomDestination.equals(subscription.getDestination()));
	}
}
