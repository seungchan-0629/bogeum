package com.bogeum.domain.chat.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.common.enums.ChatStatus;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅방. 매물 1개 기준으로 문의자 1명과 중개사 1명으로 구성한다 (CHAT_002)
 */
@Entity
@Table(name = "chat_room", uniqueConstraints = {
	@UniqueConstraint(columnNames = {"property_id", "inquirer_id", "agent_id"})
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long chatRoomId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "property_id", nullable = false)
	private Property property;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private ChatRoomType roomType;

	// 문의한 임차인 또는 중개를 신청한 임대인
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "inquirer_id", nullable = false)
	private User inquirer;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "agent_id", nullable = false)
	private User agent;

	// 매물이 삭제되거나 계약이 체결되면 읽기 전용이 된다 (CHAT_009)
	@Builder.Default
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private ChatStatus chatStatus = ChatStatus.ACTIVE;

	// 안 읽은 메시지 수 계산에 쓴다 (CHAT_007)
	private Long inquirerLastReadMessageId;

	private Long agentLastReadMessageId;

	// 채팅 목록을 최근 대화 순으로 정렬하는 기준 (CHAT_005)
	private LocalDateTime lastMessageAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
