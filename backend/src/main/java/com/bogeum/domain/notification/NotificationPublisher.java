package com.bogeum.domain.notification;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.bogeum.domain.notification.model.NotificationDto;

import lombok.RequiredArgsConstructor;

/**
 * 안 읽은 알림 수를 그 사람의 STOMP 연결로 보낸다 (NOTIFY_007, NOTIFY_021).
 * 헤더의 종 아이콘이 새로고침 없이 바뀐다
 */
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

	private final SimpMessagingTemplate messagingTemplate;

	// 저장이 확정된 뒤에만 보낸다. 되돌려진 알림이 숫자에 잡히지 않게 한다
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(NotificationUnreadCountChangedEvent event) {
		messagingTemplate.convertAndSendToUser(String.valueOf(event.userId()), "/queue/notifications",
			new NotificationDto.UnreadCountResponse(event.unreadCount()));
	}
}
