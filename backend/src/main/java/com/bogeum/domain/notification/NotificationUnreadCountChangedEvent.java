package com.bogeum.domain.notification;

/** 한 사람의 안 읽은 알림 수가 바뀌었다. 저장이 확정되면 NotificationPublisher가 알린다 (NOTIFY_007, NOTIFY_021) */
public record NotificationUnreadCountChangedEvent(Long userId, Long unreadCount) {
}
