package com.bogeum.domain.notification;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.common.enums.NotificationRefType;
import com.bogeum.common.enums.NotificationType;
import com.bogeum.domain.notification.model.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	// 한 사람이 한 대상(채팅방 등)에 대해 받은 안 읽은 알림
	List<Notification> findAllByUser_UserIdAndTypeAndRefTypeAndRefIdAndReadAtIsNull(Long userId, NotificationType type,
		NotificationRefType refType, Long refId);
}
