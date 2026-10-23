package com.bogeum.domain.chatbot.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.bogeum.domain.user.model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 챗봇 보고서 이력. 언제든 다시 내려받을 수 있게 남긴다 (CHATBOT_008)
 */
@Entity
@Table(name = "chatbot_report")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long chatbotReportId;

	// 보고서를 요청한 관리자
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	// S3에 업로드한 PDF 보고서 파일의 키
	@Column(nullable = false, length = 500)
	private String fileKey;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
