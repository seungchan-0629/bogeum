package com.bogeum.domain.agent.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

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
 * 중개사 인증 서류 (USER_021)
 */
@Entity
@Table(name = "agent_document")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDocument {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long agentDocumentId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "agent_profile_id", nullable = false)
	private Agent agentProfile;

	// S3에 저장한 공인중개사 관련 서류 파일의 키
	@Column(nullable = false, length = 500)
	private String fileKey;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
