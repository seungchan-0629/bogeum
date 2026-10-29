package com.bogeum.domain.agent.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.bogeum.common.enums.ApprovalStatus;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 중개사 정보
 */
@Entity
@Table(name = "agent_profile")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Agent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long agentProfileId;

	// 사용자 한 명에 하나만 만든다
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	// 관리자가 중개사를 검색하는 기준 (AGENT_001)
	@Column(nullable = false, length = 100)
	private String officeName;

	// 관리자가 중개사를 검색하는 기준. 마이페이지에서 수정한다 (USER_020, AGENT_001)
	@Column(nullable = false)
	private String officeAddress;

	// 관리자가 최종 승인해야 중개사로 서비스를 이용할 수 있다 (AGENT_002, USER_021)
	@Builder.Default
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

	// 반려 알림에 함께 안내한다 (NOTIFY_025)
	private String rejectReason;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reviewed_by")
	private User reviewedBy;

	private LocalDateTime reviewedAt;

	// 가입 후 2~3일. 기한이 임박하면 알림을 보낸다 (USER_021, NOTIFY_024)
	private LocalDateTime documentDueAt;

	/** 실습 승인이다. 관리자 심사 기록으로 오인하지 않도록 reviewedBy는 비운다. */
	public void approveForDemo() {
		this.approvalStatus = ApprovalStatus.APPROVED;
		this.rejectReason = null;
		this.reviewedBy = null;
		this.reviewedAt = LocalDateTime.now();
	}

	// 신규 가입 신청을 목록 최상단에 보여주는 기준 (AGENT_001)
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private LocalDateTime updatedAt;
}
