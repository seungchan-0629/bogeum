package com.bogeum.domain.aftersales.model;

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
 * AS 답변 댓글. 임대인이 조치 사항을 남긴다 (AS_003)
 */
@Entity
@Table(name = "as_comment")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSalesComment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long asCommentId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "as_request_id", nullable = false)
	private AfterSalesRequest asRequest;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "author_id", nullable = false)
	private User author;

	@Column(nullable = false, length = 1000)
	private String content;

	// 임차인에게 응답 알림을 보내는 기준 (NOTIFY_016)
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
