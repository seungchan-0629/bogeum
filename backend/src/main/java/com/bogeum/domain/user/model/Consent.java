package com.bogeum.domain.user.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.bogeum.common.enums.ConsentType;

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
 * 개인정보 동의 (USER_004, USER_017)
 */
@Entity
@Table(name = "user_consent", uniqueConstraints = {
	@UniqueConstraint(columnNames = {"user_id", "consent_type"})
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Consent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long userConsentId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private ConsentType consentType;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime agreedAt;
}
