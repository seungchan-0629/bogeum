package com.bogeum.domain;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이메일이 바뀌어도 제공자와 고유 사용자 ID로 같은 회원을 찾는다. */
@Entity
@Table(name = "social_accounts", uniqueConstraints = {
	@UniqueConstraint(name = "uk_social_provider_user", columnNames = {"provider", "provider_user_id"}),
	@UniqueConstraint(name = "uk_social_member_provider", columnNames = {"member_id", "provider"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(nullable = false, length = 20)
	private String provider;

	@Column(name = "provider_user_id", nullable = false, length = 255)
	private String providerUserId;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public SocialAccount(Member member, String provider, String providerUserId) {
		this.member = member;
		this.provider = provider;
		this.providerUserId = providerUserId;
		this.createdAt = Instant.now();
	}
}
