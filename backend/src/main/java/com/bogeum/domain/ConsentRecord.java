package com.bogeum.domain;

import java.time.Instant;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 어떤 종류·버전의 약관에 언제 동의했는지 보관한다. 회원·항목·버전 조합은 중복 저장할 수 없다.
 */
@Entity
@Table(name = "consent_records", uniqueConstraints =
	@UniqueConstraint(name = "uk_consent_member_type_version", columnNames = {"member_id", "consent_type", "terms_version"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(name = "consent_type", nullable = false, length = 30)
	private ConsentType consentType;

	@Column(name = "terms_version", nullable = false, length = 50)
	private String termsVersion;

	@Column(nullable = false)
	private Instant agreedAt;

	public ConsentRecord(Member member, ConsentType consentType, String termsVersion) {
		this.member = member;
		this.consentType = consentType;
		this.termsVersion = termsVersion;
		this.agreedAt = Instant.now();
	}
}
