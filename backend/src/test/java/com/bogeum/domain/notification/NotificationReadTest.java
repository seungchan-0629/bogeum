package com.bogeum.domain.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.bogeum.common.enums.NotificationRefType;
import com.bogeum.common.enums.NotificationType;
import com.bogeum.common.enums.Role;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.notification.model.Notification;
import com.bogeum.domain.notification.model.NotificationDto;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.model.User;

/**
 * 내 알림 목록·안 읽은 수·읽음 처리를 H2로 확인한다 (NOTIFY_007, NOTIFY_021).
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-notification-read-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(NotificationService.class)
@RecordApplicationEvents
class NotificationReadTest {

	@Autowired
	private NotificationService notificationService;
	@Autowired
	private NotificationRepository notificationRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ApplicationEvents applicationEvents;

	private User me;
	private User other;

	@BeforeEach
	void setUp() {
		me = user("최세입");
		other = user("김중개");
	}

	@Test
	@DisplayName("내 알림만 최신순으로 size개 주고, cursor로 그보다 오래된 알림을 이어서 준다")
	void readsLatestFirstWithCursor() {
		Notification first = notification(me, "첫 번째");
		Notification second = notification(me, "두 번째");
		Notification third = notification(me, "세 번째");
		notification(other, "남의 알림");

		NotificationDto.ListResponse page = notificationService.readNotifications(me.getUserId(), null, 2, false);

		assertEquals(List.of(third.getNotificationId(), second.getNotificationId()), ids(page));
		assertTrue(page.getHasNext());
		assertEquals(second.getNotificationId(), page.getNextCursor());

		NotificationDto.ListResponse next = notificationService.readNotifications(me.getUserId(), page.getNextCursor(), 2,
			false);
		assertEquals(List.of(first.getNotificationId()), ids(next));
		assertFalse(next.getHasNext());
		assertNull(next.getNextCursor());
	}

	@Test
	@DisplayName("알림 한 건에 종류·문구·이동 대상·읽음 여부를 담는다")
	void mapsItem() {
		Notification notification = notification(me, "새 메시지");

		NotificationDto.ItemResponse item = notificationService.readNotifications(me.getUserId(), null, 20, false)
			.getNotifications().get(0);

		assertEquals(notification.getNotificationId(), item.getNotificationId());
		assertEquals(NotificationType.CHAT_MESSAGE, item.getType());
		assertEquals("새 메시지", item.getTitle());
		assertEquals("김중개: 안녕하세요", item.getBody());
		assertEquals(NotificationRefType.CHAT_ROOM, item.getRefType());
		assertEquals(3L, item.getRefId());
		assertFalse(item.getIsRead());
		assertNotNull(item.getCreatedAt());
	}

	@Test
	@DisplayName("unreadOnly면 안 읽은 알림만 준다")
	void readsUnreadOnly() {
		Notification read = notification(me, "읽은 알림");
		Notification unread = notification(me, "안 읽은 알림");
		notificationService.updateRead(me.getUserId(), read.getNotificationId());

		NotificationDto.ListResponse page = notificationService.readNotifications(me.getUserId(), null, 20, true);

		assertEquals(List.of(unread.getNotificationId()), ids(page));
	}

	@Test
	@DisplayName("size가 1~20을 벗어나면 INVALID_REQUEST다")
	void rejectsInvalidSize() {
		NotificationException zero = assertThrows(NotificationException.class,
			() -> notificationService.readNotifications(me.getUserId(), null, 0, false));
		NotificationException tooMany = assertThrows(NotificationException.class,
			() -> notificationService.readNotifications(me.getUserId(), null, 21, false));

		assertEquals(ErrorCode.INVALID_REQUEST, zero.getErrorCode());
		assertEquals(ErrorCode.INVALID_REQUEST, tooMany.getErrorCode());
	}

	@Test
	@DisplayName("안 읽은 수는 내 알림 중 안 읽은 것만 센다")
	void readsUnreadCount() {
		Notification read = notification(me, "읽은 알림");
		notification(me, "안 읽은 알림");
		notification(other, "남의 알림");
		notificationService.updateRead(me.getUserId(), read.getNotificationId());

		assertEquals(1L, notificationService.readUnreadCount(me.getUserId()).getUnreadCount());
	}

	@Test
	@DisplayName("알림을 읽으면 읽은 시각이 남고 바뀐 안 읽은 수를 알린다. 다시 읽어도 시각은 그대로다")
	void updatesRead() {
		Notification notification = notification(me, "새 메시지");
		notification(me, "다른 알림");
		long eventsBefore = applicationEvents.stream(NotificationUnreadCountChangedEvent.class).count();

		notificationService.updateRead(me.getUserId(), notification.getNotificationId());
		var readAt = notification.getReadAt();
		notificationService.updateRead(me.getUserId(), notification.getNotificationId());

		assertNotNull(readAt);
		assertEquals(readAt, notification.getReadAt());
		List<NotificationUnreadCountChangedEvent> events = applicationEvents.stream(NotificationUnreadCountChangedEvent.class)
			.skip(eventsBefore).toList();
		// 이미 읽은 알림을 다시 읽을 때는 숫자가 안 바뀌어 알리지 않는다
		assertEquals(List.of(new NotificationUnreadCountChangedEvent(me.getUserId(), 1L)), events);
	}

	@Test
	@DisplayName("남의 알림이나 없는 알림을 읽으면 NOTIFICATION_NOT_FOUND다")
	void rejectsOthersNotification() {
		Notification othersNotification = notification(other, "남의 알림");

		NotificationException others = assertThrows(NotificationException.class,
			() -> notificationService.updateRead(me.getUserId(), othersNotification.getNotificationId()));
		NotificationException missing = assertThrows(NotificationException.class,
			() -> notificationService.updateRead(me.getUserId(), 999_999L));

		assertEquals(ErrorCode.NOTIFICATION_NOT_FOUND, others.getErrorCode());
		assertEquals(ErrorCode.NOTIFICATION_NOT_FOUND, missing.getErrorCode());
		assertNull(othersNotification.getReadAt());
	}

	@Test
	@DisplayName("모두 읽음은 내 안 읽은 알림만 읽음으로 바꾸고 안 읽은 수 0을 알린다")
	void updatesReadAll() {
		notification(me, "첫 번째");
		notification(me, "두 번째");
		Notification othersNotification = notification(other, "남의 알림");
		long eventsBefore = applicationEvents.stream(NotificationUnreadCountChangedEvent.class).count();

		notificationService.updateReadAll(me.getUserId());

		assertEquals(0L, notificationService.readUnreadCount(me.getUserId()).getUnreadCount());
		assertNull(othersNotification.getReadAt());
		assertEquals(List.of(new NotificationUnreadCountChangedEvent(me.getUserId(), 0L)),
			applicationEvents.stream(NotificationUnreadCountChangedEvent.class).skip(eventsBefore).toList());
	}

	@Test
	@DisplayName("알림을 만들면 받는 사람의 바뀐 안 읽은 수를 알린다")
	void publishesUnreadCountOnCreate() {
		notification(me, "첫 번째");
		notification(me, "두 번째");

		List<NotificationUnreadCountChangedEvent> events = applicationEvents.stream(NotificationUnreadCountChangedEvent.class)
			.toList();
		assertEquals(List.of(new NotificationUnreadCountChangedEvent(me.getUserId(), 1L),
			new NotificationUnreadCountChangedEvent(me.getUserId(), 2L)), events);
	}

	private Notification notification(User user, String title) {
		return notificationService.createNotification(user, NotificationType.CHAT_MESSAGE, title, "김중개: 안녕하세요",
			NotificationRefType.CHAT_ROOM, 3L);
	}

	private List<Long> ids(NotificationDto.ListResponse page) {
		return page.getNotifications().stream().map(NotificationDto.ItemResponse::getNotificationId).toList();
	}

	private User user(String name) {
		return userRepository.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash")
			.name(name).phone("010" + (int) (Math.random() * 100_000_000)).role(Role.USER).build());
	}
}
