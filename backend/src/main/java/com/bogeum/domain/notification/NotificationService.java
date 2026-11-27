package com.bogeum.domain.notification;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.common.enums.MessageType;
import com.bogeum.common.enums.NotificationRefType;
import com.bogeum.common.enums.NotificationType;
import com.bogeum.domain.notification.model.Notification;
import com.bogeum.domain.notification.model.NotificationDto;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

	private static final String CHAT_MESSAGE_TITLE = "새 메시지";
	private static final String BROKERAGE_REQUESTED_TITLE = "새 중개 신청";
	private static final int CHAT_PREVIEW_MAX_LENGTH = 100;
	private static final int MAX_PAGE_SIZE = 20;

	private final NotificationRepository notificationRepository;
	private final UserRepository userRepository;
	private final ApplicationEventPublisher eventPublisher;

	/** 알림 한 건을 만든다. 각 기능의 알림은 이 메서드로 만든다 (NOTIFY_001) */
	@Transactional
	public Notification createNotification(User user, NotificationType type, String title, String body,
			NotificationRefType refType, Long refId) {
		Notification notification = notificationRepository.save(Notification.create(user, type, title, body, refType, refId));
		publishUnreadCount(user.getUserId());
		return notification;
	}

	/**
	 * 채팅방을 보고 있지 않은 상대에게 새 메시지 알림을 만든다 (CHAT_008, NOTIFY_023).
	 * 같은 방의 안 읽은 알림은 하나만 두려고 지우고 최신 메시지로 새로 만들어 맨 위에 오게 한다.
	 * 메시지 저장이 끝난 뒤 불리므로 새 트랜잭션에서 저장한다
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void createChatMessageNotification(Long recipientId, Long senderId, Long chatRoomId, MessageType messageType,
			String content) {
		notificationRepository.deleteAll(unreadChatNotifications(recipientId, chatRoomId));
		User sender = userRepository.findById(senderId).orElseThrow();
		String preview = messageType == MessageType.IMAGE ? "사진을 보냈어요." : content.strip();
		if (preview.length() > CHAT_PREVIEW_MAX_LENGTH) {
			preview = preview.substring(0, CHAT_PREVIEW_MAX_LENGTH) + "…";
		}
		createNotification(userRepository.getReferenceById(recipientId), NotificationType.CHAT_MESSAGE, CHAT_MESSAGE_TITLE,
			sender.getName() + ": " + preview, NotificationRefType.CHAT_ROOM, chatRoomId);
	}

	/**
	 * 임대인이 중개를 신청하면 배정된 중개사에게 알림을 만든다 (NOTIFY_022).
	 * 지정 신청과 랜덤 배정 모두 중개사가 할 일(수락)은 같아서 문구를 나누지 않는다.
	 * 신청 저장이 끝난 뒤 불리므로 새 트랜잭션에서 저장한다
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void createBrokerageRequestNotification(Long agentUserId, Long brokerageRequestId, String landlordName,
			String roadAddress) {
		createNotification(userRepository.getReferenceById(agentUserId), NotificationType.BROKERAGE_REQUESTED,
			BROKERAGE_REQUESTED_TITLE, landlordName + "님이 " + roadAddress + " 매물 중개를 신청했어요.",
			NotificationRefType.BROKERAGE_REQUEST, brokerageRequestId);
	}

	/**
	 * 내 알림을 최신순으로 size개 준다. cursor가 있으면 그보다 오래된 것, unreadOnly면 안 읽은 것만 준다 (NOTIFY_007, NOTIFY_021).
	 * 알림 화면은 20개씩, 헤더 종 아이콘 미리보기는 5개를 받는다
	 */
	@Transactional(readOnly = true)
	public NotificationDto.ListResponse readNotifications(Long userId, Long cursor, int size, boolean unreadOnly) {
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw NotificationException.invalidRequest();
		}
		// 하나 더 받아서 더 오래된 알림이 남았는지 안다
		List<Notification> found = notificationRepository.findPage(userId, cursor, unreadOnly, Limit.of(size + 1));
		boolean hasNext = found.size() > size;
		List<NotificationDto.ItemResponse> notifications = found.subList(0, Math.min(found.size(), size)).stream()
			.map(NotificationDto.ItemResponse::from)
			.toList();
		Long nextCursor = hasNext ? notifications.get(notifications.size() - 1).getNotificationId() : null;
		return new NotificationDto.ListResponse(notifications, nextCursor, hasNext);
	}

	@Transactional(readOnly = true)
	public NotificationDto.UnreadCountResponse readUnreadCount(Long userId) {
		return new NotificationDto.UnreadCountResponse(notificationRepository.countByUser_UserIdAndReadAtIsNull(userId));
	}

	/** 알림을 눌러 읽음으로 바꾼다. 이미 읽었으면 그대로 둔다. 내 알림이 아니면 있는지도 알리지 않는다 (NOTIFY_007, NOTIFY_021) */
	@Transactional
	public void updateRead(Long userId, Long notificationId) {
		Notification notification = notificationRepository.findByNotificationIdAndUser_UserId(notificationId, userId)
			.orElseThrow(NotificationException::notificationNotFound);
		if (notification.getReadAt() == null) {
			notification.read();
			publishUnreadCount(userId);
		}
	}

	/** 안 읽은 내 알림을 모두 읽음으로 바꾼다 (NOTIFY_007, NOTIFY_021) */
	@Transactional
	public void updateReadAll(Long userId) {
		readAll(notificationRepository.findAllByUser_UserIdAndReadAtIsNull(userId), userId);
	}

	/** 채팅방에서 메시지를 읽으면 그 방의 새 메시지 알림도 읽음으로 바꾼다 (CHAT_008) */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void updateChatMessageRead(Long userId, Long chatRoomId) {
		readAll(unreadChatNotifications(userId, chatRoomId), userId);
	}

	private void readAll(List<Notification> unreadNotifications, Long userId) {
		if (unreadNotifications.isEmpty()) {
			return;
		}
		unreadNotifications.forEach(Notification::read);
		publishUnreadCount(userId);
	}

	// 숫자는 확정된 뒤 NotificationPublisher가 보낸다. 같은 트랜잭션에서 세어 방금 바뀐 것까지 들어간다
	private void publishUnreadCount(Long userId) {
		notificationRepository.flush();
		eventPublisher.publishEvent(new NotificationUnreadCountChangedEvent(userId,
			notificationRepository.countByUser_UserIdAndReadAtIsNull(userId)));
	}

	private List<Notification> unreadChatNotifications(Long userId, Long chatRoomId) {
		return notificationRepository.findAllByUser_UserIdAndTypeAndRefTypeAndRefIdAndReadAtIsNull(userId,
			NotificationType.CHAT_MESSAGE, NotificationRefType.CHAT_ROOM, chatRoomId);
	}
}
