package com.bogeum.domain.notification.model;

import java.time.LocalDateTime;
import java.util.List;

import com.bogeum.common.enums.NotificationRefType;
import com.bogeum.common.enums.NotificationType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class NotificationDto {

	/** 내 알림 한 묶음. 최신순이다 (NOTIFY_007, NOTIFY_021) */
	@Getter
	@AllArgsConstructor
	public static class ListResponse {

		private List<ItemResponse> notifications;
		// 더 오래된 알림을 받을 때 보낼 cursor. 더 없으면 비어 있다
		private Long nextCursor;
		private Boolean hasNext;
	}

	/** 알림 한 건. 화면은 refType과 refId로 이동할 화면을 고른다 (NOTIFY_007, NOTIFY_021) */
	@Getter
	@Builder
	public static class ItemResponse {

		private Long notificationId;
		private NotificationType type;
		private String title;
		private String body;
		private NotificationRefType refType;
		private Long refId;
		private Boolean isRead;
		private LocalDateTime createdAt;

		public static ItemResponse from(Notification notification) {
			return ItemResponse.builder()
				.notificationId(notification.getNotificationId())
				.type(notification.getType())
				.title(notification.getTitle())
				.body(notification.getBody())
				.refType(notification.getRefType())
				.refId(notification.getRefId())
				.isRead(notification.getReadAt() != null)
				.createdAt(notification.getCreatedAt())
				.build();
		}
	}

	/** 안 읽은 알림 수. 헤더의 종 아이콘에 표시한다. 바뀌면 실시간으로도 보낸다 (NOTIFY_007, NOTIFY_021) */
	@Getter
	@AllArgsConstructor
	public static class UnreadCountResponse {

		private Long unreadCount;
	}
}
