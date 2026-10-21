package com.bogeum.security.dto;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import com.bogeum.domain.AccountStatus;
import com.bogeum.domain.Member;

import lombok.Getter;

/**
 * DB의 Member를 Spring Security가 사용하는 로그인 주체(UserDetails)로 변환한다.
 * 회원 ID, 비밀번호 해시, 활성 여부, ROLE_ 접두사의 권한을 인증 과정에 제공한다.
 */
@Getter
public class MemberSecurityDTO extends User {

	private static final long serialVersionUID = 1L;

	private final Long memberId;

	public MemberSecurityDTO(Member member) {
		super(member.getEmail(), member.getPasswordHash(),
			member.getStatus() == AccountStatus.ACTIVE, true, true, true,
			List.of(new SimpleGrantedAuthority("ROLE_" + member.getMemberType().name())));
		this.memberId = member.getId();
	}
}
