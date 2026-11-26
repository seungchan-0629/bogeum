package com.bogeum.domain.notification;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bogeum.common.enums.NotificationRefType;
import com.bogeum.common.enums.NotificationType;
import com.bogeum.domain.notification.model.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	// 내 알림 최신순. cursor가 있으면 그보다 오래된 것, unreadOnly면 안 읽은 것만 (NOTIFY_007, NOTIFY_021)
	@Query("""
		select n from Notification n
		where n.user.userId = :userId
			and (:cursor is null or n.notificationId < :cursor)
			and (:unreadOnly = false or n.readAt is null)
		order by n.notificationId desc
		""")
	List<Notification> findPage(@Param("userId") Long userId, @Param("cursor") Long cursor,
		@Param("unreadOnly") boolean unreadOnly, Limit limit);

	long countByUser_UserIdAndReadAtIsNull(Long userId);

	Optional<Notification> findByNotificationIdAndUser_UserId(Long notificationId, Long userId);

	List<Notification> findAllByUser_UserIdAndReadAtIsNull(Long userId);

	// 한 사람이 한 대상(채팅방 등)에 대해 받은 안 읽은 알림
	List<Notification> findAllByUser_UserIdAndTypeAndRefTypeAndRefIdAndReadAtIsNull(Long userId, NotificationType type,
		NotificationRefType refType, Long refId);
}
