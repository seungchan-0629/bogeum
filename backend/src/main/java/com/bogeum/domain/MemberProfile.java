package com.bogeum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일반 회원의 본인 인증 정보를 저장하는 프로필.
 */
@Entity
@Table(name = "member_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberProfile {

	@Id
	private Long memberId;

	// 회원 ID를 프로필의 기본 키이자 외래 키로 공유해 회원당 하나의 일반 프로필을 연결한다.
	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id")
	private Member member;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false, length = 20)
	private String phoneNumber;

	public MemberProfile(Member member, String name, String phoneNumber) {
		this.member = member;
		this.name = name;
		this.phoneNumber = phoneNumber;
	}
}
