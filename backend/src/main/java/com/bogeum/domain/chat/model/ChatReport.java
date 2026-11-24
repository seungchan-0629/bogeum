package com.bogeum.domain.chat.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.bogeum.common.enums.ChatReportReason;
import com.bogeum.common.enums.ChatReportStatus;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅 신고. 신고된 채팅방만 관리자가 대화를 열람하고 AI 요약을 받을 수 있다 (CHAT_001, CHATBOT_009)
 */
@Entity
@Table(name = "chat_report")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long chatReportId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "chat_room_id", nullable = false)
	private ChatRoom chatRoom;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reporter_id", nullable = false)
	private User reporter;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private ChatReportReason reasonType;

	@Column(length = 1000)
	private String content;

	// 기존 신고에도 값이 들어가게 DB 기본값을 둔다 (CHAT_001)
	@Builder.Default
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@ColumnDefault("'RECEIVED'")
	@Column(nullable = false, length = 20)
	private ChatReportStatus chatReportStatus = ChatReportStatus.RECEIVED;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	// 채팅방에서 상대를 관리자에게 신고한다. 신고된 방만 관리자가 대화를 열람한다 (CHAT_011)
	public static ChatReport create(ChatRoom chatRoom, User reporter, ChatReportReason reasonType, String content) {
		return ChatReport.builder().chatRoom(chatRoom).reporter(reporter).reasonType(reasonType).content(content).build();
	}

	// 관리자가 대화를 처음 열면 확인으로 바꾼다. 이미 확인했거나 처리했으면 그대로 둔다 (CHAT_001)
	public void check() {
		if (chatReportStatus == ChatReportStatus.RECEIVED) {
			chatReportStatus = ChatReportStatus.CHECKED;
		}
	}
}
