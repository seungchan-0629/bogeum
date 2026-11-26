package com.bogeum.domain.notification;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.common.enums.MessageType;
import com.bogeum.common.enums.NotificationRefType;
import com.bogeum.common.enums.NotificationType;
import com.bogeum.domain.notification.model.Notification;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

	private static final String CHAT_MESSAGE_TITLE = "새 메시지";
	private static final int CHAT_PREVIEW_MAX_LENGTH = 100;

	private final NotificationRepository notificationRepository;
	private final UserRepository userRepository;

	/** 알림 한 건을 만든다. 각 기능의 알림은 이 메서드로 만든다 (NOTIFY_001) */
	@Transactional
	public Notification createNotification(User user, NotificationType type, String title, String body,
			NotificationRefType refType, Long refId) {
		return notificationRepository.save(Notification.create(user, type, title, body, refType, refId));
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

	/** 채팅방에서 메시지를 읽으면 그 방의 새 메시지 알림도 읽음으로 바꾼다 (CHAT_008) */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void updateChatMessageRead(Long userId, Long chatRoomId) {
		unreadChatNotifications(userId, chatRoomId).forEach(Notification::read);
	}

	private List<Notification> unreadChatNotifications(Long userId, Long chatRoomId) {
		return notificationRepository.findAllByUser_UserIdAndTypeAndRefTypeAndRefIdAndReadAtIsNull(userId,
			NotificationType.CHAT_MESSAGE, NotificationRefType.CHAT_ROOM, chatRoomId);
	}
}
