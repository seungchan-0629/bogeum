package com.bogeum.domain.notification;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bogeum.domain.notification.model.NotificationDto;
import com.bogeum.domain.user.model.UserDto.LoginUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	// 화면 주소 /notifications와 겹치지 않게 /me를 붙였다 (관심 매물 /favorites/me와 같다)
	// 내 알림 최신순. cursor가 없으면 최근 것부터, 있으면 그보다 오래된 것. size는 1~20이다 (NOTIFY_007, NOTIFY_021)
	@GetMapping("/notifications/me")
	public NotificationDto.ListResponse readNotifications(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(value = "cursor", required = false) Long cursor,
			@RequestParam(value = "size", defaultValue = "20") int size,
			@RequestParam(value = "unreadOnly", defaultValue = "false") boolean unreadOnly) {
		return notificationService.readNotifications(loginUser.getUserId(), cursor, size, unreadOnly);
	}

	// 헤더 종 아이콘에 표시할 안 읽은 알림 수 (NOTIFY_007, NOTIFY_021)
	@GetMapping("/notifications/unread-count")
	public NotificationDto.UnreadCountResponse readUnreadCount(@AuthenticationPrincipal LoginUser loginUser) {
		return notificationService.readUnreadCount(loginUser.getUserId());
	}

	// 알림을 누르면 읽음으로 바꾼다. 돌려줄 내용이 없어 204다. 내 알림이 아니면 NOTIFICATION_NOT_FOUND
	@PatchMapping("/notifications/{notificationId}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void updateRead(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable("notificationId") Long notificationId) {
		notificationService.updateRead(loginUser.getUserId(), notificationId);
	}

	// 안 읽은 내 알림을 모두 읽음으로 바꾼다
	@PatchMapping("/notifications/read-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void updateReadAll(@AuthenticationPrincipal LoginUser loginUser) {
		notificationService.updateReadAll(loginUser.getUserId());
	}
}
