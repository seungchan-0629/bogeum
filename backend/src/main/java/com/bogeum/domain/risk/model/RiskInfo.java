package com.bogeum.domain.risk.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.bogeum.common.enums.RiskReason;
import com.bogeum.common.enums.RiskStatus;
import com.bogeum.common.enums.RiskTargetType;
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
 * 위험 정보. 대상은 사용자(임대인, 임차인, 중개사) 또는 매물이다 (RISK_001, RISK_004)
 */
@Entity
@Table(name = "risk_info")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskInfo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long riskInfoId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private RiskTargetType targetType;

	// target_type이 USER일 때만 채운다
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "target_user_id")
	private User targetUser;

	// target_type이 PROPERTY일 때만 채운다
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "target_property_id")
	private Property targetProperty;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private RiskReason reasonType;

	// 임차인과 임대인에게는 보여주지 않는다 (RISK_006, RISK_007)
	@Column(columnDefinition = "TEXT")
	private String detail;

	// S3에 저장한 근거 자료 파일의 키 (RISK_002)
	@Column(length = 500)
	private String evidenceFileKey;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private RiskStatus riskStatus;

	// 시스템이 만든 후보는 비어 있다 (RENT_007)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "registered_by")
	private User registeredBy;

	// 수정할 때 입력한 사유 (RISK_003)
	private String updateReason;

	// 비어 있으면 해제되지 않은 정보
	private LocalDateTime releasedAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private LocalDateTime updatedAt;
}
