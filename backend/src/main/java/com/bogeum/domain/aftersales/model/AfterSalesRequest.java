package com.bogeum.domain.aftersales.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.bogeum.common.enums.AsStatus;
import com.bogeum.domain.aftersales.AfterSalesException;
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
 * AS 요청 (AS_001)
 */
@Entity
@Table(name = "as_request")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSalesRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long asRequestId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "property_id", nullable = false)
	private Property property;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tenant_id", nullable = false)
	private User tenant;

	// 챗봇이 정리한 내용도 임차인이 확인한 뒤 여기에 저장한다 (CHATBOT_005)
	@Column(nullable = false, length = 2000)
	private String content;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private AsStatus asStatus = AsStatus.RECEIVED;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	/** 완료된 접수는 댓글을 추가하거나 다시 열 수 없다. 서비스는 소유 권한 확인 후 호출한다 (AS_003). */
	public void validateNotCompleted() {
		if (asStatus == AsStatus.COMPLETED) {
			throw AfterSalesException.alreadyCompleted();
		}
	}

	/** 완료 전에는 자유롭게 변경하되 완료 상태는 재개하지 않는다. 같은 상태 재요청은 변경 없이 성공한다. */
	public void changeStatus(AsStatus nextStatus) {
		if (asStatus == nextStatus) {
			return;
		}
		validateNotCompleted();
		asStatus = nextStatus;
	}
}
