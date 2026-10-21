package com.bogeum.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 중개사 계정의 연락처·사무소 정보·서류 심사 상태를 저장하는 프로필.
 */
@Entity
@Table(name = "agent_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentProfile {

	@Id
	private Long memberId;

	// 회원 ID를 프로필의 기본 키이자 외래 키로 공유해 회원당 하나의 중개사 프로필을 연결한다.
	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id")
	private Member member;

	@Column(nullable = false, length = 20)
	private String phoneNumber;

	@Column(nullable = false, length = 100)
	private String officeName;

	@Column(nullable = false, length = 300)
	private String officeAddress;

	@Column(nullable = false, length = 20)
	private String officePhoneNumber;

	@Column(length = 50)
	private String officeRegistrationNumber;

	@Column(length = 50)
	private String representativeName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AgentVerificationStatus verificationStatus;

	@Column(nullable = false)
	private Instant documentSubmissionDeadline;

	@Builder
	public AgentProfile(Member member, String phoneNumber, String officeName, String officeAddress,
			String officePhoneNumber, String officeRegistrationNumber, String representativeName,
			Instant documentSubmissionDeadline) {
		this.member = member;
		this.phoneNumber = phoneNumber;
		this.officeName = officeName;
		this.officeAddress = officeAddress;
		this.officePhoneNumber = officePhoneNumber;
		this.officeRegistrationNumber = officeRegistrationNumber;
		this.representativeName = representativeName;
		// 가입 완료가 서류 승인 완료를 의미하지 않으므로 모든 신규 프로필은 제출 필요 상태로 시작한다.
		this.verificationStatus = AgentVerificationStatus.DOCUMENT_REQUIRED;
		this.documentSubmissionDeadline = documentSubmissionDeadline;
	}
}
