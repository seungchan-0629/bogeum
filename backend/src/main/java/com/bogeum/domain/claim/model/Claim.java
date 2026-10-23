package com.bogeum.domain.claim.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.bogeum.common.enums.ClaimReason;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 클레임. 임대인과 임차인이 매물별로 상대방에 대해 접수한다 (CLAIM_001)
 */
@Entity
@Table(name = "claim")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Claim {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long claimId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "property_id", nullable = false)
	private Property property;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "claimant_id", nullable = false)
	private User claimant;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "target_user_id", nullable = false)
	private User targetUser;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private ClaimReason reasonType;

	@Column(length = 2000)
	private String content;

	// 비어 있으면 인계 전 (NOTIFY_019)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "handed_over_agent_id")
	private User handedOverAgent;

	// 이후 조치 댓글이 일정 기간 없으면 관리자에게 알린다 (NOTIFY_005)
	private LocalDateTime handedOverAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
