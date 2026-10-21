package com.bogeum.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모든 회원 유형이 공유하는 로그인 계정 엔티티. 유형별 추가 정보는 별도 프로필에 저장한다.
 */
@Entity
@Table(name = "members", uniqueConstraints = @UniqueConstraint(name = "uk_members_email", columnNames = "email"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 254)
	private String email;

	// 평문이 아니라 PasswordEncoder가 생성한 해시만 보관한다.
	@Column(nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberType memberType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountStatus status;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public Member(String email, String passwordHash, MemberType memberType) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.memberType = memberType;
		this.status = AccountStatus.ACTIVE;
		this.createdAt = Instant.now();
	}

	public void suspend() {
		// 상태만 변경한다. 로그인 검증 및 현재 회원 조회에서 이 값을 확인하며 세션 자체를 삭제하지는 않는다.
		this.status = AccountStatus.SUSPENDED;
	}
}
