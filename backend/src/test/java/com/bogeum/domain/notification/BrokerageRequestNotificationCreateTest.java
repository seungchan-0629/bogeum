package com.bogeum.domain.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.NotificationRefType;
import com.bogeum.common.enums.NotificationType;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.notification.model.Notification;
import com.bogeum.domain.notification.model.NotificationDto;
import com.bogeum.domain.property.BrokerageRequestCreatedEvent;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.model.User;

/**
 * 중개 신청 사건이 확정되면 배정된 중개사에게 알림이 생기는지 H2로 확인한다 (NOTIFY_022).
 * 사건을 알리는 쪽은 PropertyServiceTest에서 보고, 여기서는 사건을 받은 뒤를 본다.
 * 저장 확정 뒤에 도는 리스너까지 보려고 테스트 트랜잭션 없이 실행하고 끝나면 지운다
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-brokerage-notification-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ NotificationService.class, NotificationEventListener.class, NotificationPublisher.class })
class BrokerageRequestNotificationCreateTest {

	private static final String ADDRESS = "서울특별시 마포구 월드컵로 61";

	@Autowired
	private ApplicationEventPublisher eventPublisher;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private NotificationRepository notificationRepository;
	@Autowired
	private PropertyRepository propertyRepository;
	@Autowired
	private UserRepository userRepository;
	@MockitoBean
	private SimpUserRegistry simpUserRegistry;
	@MockitoBean
	private SimpMessagingTemplate messagingTemplate;

	private User landlord;
	private User agent;

	@BeforeEach
	void setUp() {
		landlord = user("박임대");
		agent = user("김중개");
	}

	@AfterEach
	void tearDown() {
		notificationRepository.deleteAll();
		propertyRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	@DisplayName("중개 신청이 확정되면 배정된 중개사에게 임대인 이름과 주소가 담긴 안 읽은 알림이 생긴다")
	void createsNotificationForAgent() {
		requestBrokerage(agent.getUserId(), 50L);

		List<Notification> notifications = notificationRepository.findAll();
		assertEquals(1, notifications.size());
		Notification notification = notifications.get(0);
		assertEquals(agent.getUserId(), notification.getUser().getUserId());
		assertEquals(NotificationType.BROKERAGE_REQUESTED, notification.getType());
		assertEquals("새 중개 신청", notification.getTitle());
		assertEquals("박임대님이 서울특별시 마포구 월드컵로 61 매물 중개를 신청했어요.", notification.getBody());
		assertEquals(NotificationRefType.BROKERAGE_REQUEST, notification.getRefType());
		assertEquals(50L, notification.getRefId());
		assertNull(notification.getReadAt());
	}

	@Test
	@DisplayName("알림이 생기면 확정된 뒤 중개사에게 바뀐 안 읽은 알림 수를 보낸다")
	void publishesUnreadCount() {
		requestBrokerage(agent.getUserId(), 50L);

		verify(messagingTemplate).convertAndSendToUser(eq(String.valueOf(agent.getUserId())), eq("/queue/notifications"),
			argThat(payload -> ((NotificationDto.UnreadCountResponse) payload).getUnreadCount() == 1L));
	}

	@Test
	@DisplayName("신청이 되돌려지면 알림을 만들지 않고, 알림 저장이 실패해도 신청은 그대로 남는다")
	void keepsRequestWhenNotificationFails() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			eventPublisher.publishEvent(new BrokerageRequestCreatedEvent(50L, agent.getUserId(), "박임대", ADDRESS));
			status.setRollbackOnly();
		});
		assertEquals(0, notificationRepository.count());

		// 없는 회원에게 알림을 저장하면 실패한다
		requestBrokerage(agent.getUserId() + 1000, 50L);
		assertEquals(0, notificationRepository.count());
		assertEquals(1, propertyRepository.count());
	}

	// 실제 신청처럼 매물 저장과 같은 트랜잭션에서 사건을 알린다
	private void requestBrokerage(Long agentUserId, Long brokerageRequestId) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			propertyRepository.save(Property.builder().landlord(landlord).leaseType(LeaseType.JEONSE)
				.deposit(BigDecimal.valueOf(300_000_000)).roadAddress(ADDRESS).build());
			eventPublisher.publishEvent(new BrokerageRequestCreatedEvent(brokerageRequestId, agentUserId, "박임대", ADDRESS));
		});
	}

	private User user(String name) {
		return userRepository.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash")
			.name(name).phone("010" + (int) (Math.random() * 100_000_000)).role(Role.USER).build());
	}
}
