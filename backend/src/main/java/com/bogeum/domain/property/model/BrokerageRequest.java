package com.bogeum.domain.property.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.bogeum.common.enums.BrokerageStatus;
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
 * 중개 신청 (PROPERTY_002, PROPERTY_008)
 */
@Entity
@Table(name = "brokerage_request")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrokerageRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long brokerageRequestId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "property_id", nullable = false)
	private Property property;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "landlord_id", nullable = false)
	private User landlord;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "agent_id", nullable = false)
	private User agent;

	// false면 희망 중개사가 없어 랜덤으로 정해진 건이다 (PROPERTY_002)
	@Builder.Default
	@Column(nullable = false, columnDefinition = "TINYINT(1)")
	private Boolean isDesignated = false;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private BrokerageStatus brokerageStatus = BrokerageStatus.REQUESTED;

	// 이때 임대인-중개사 채팅방이 만들어진다 (CHAT_002)
	private LocalDateTime acceptedAt;

	// 중개사에게 신청 알림을 보내는 기준 (NOTIFY_022)
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
