package com.bogeum.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.repository.MemberRepository;
import com.bogeum.security.dto.MemberSecurityDTO;

import lombok.RequiredArgsConstructor;

/**
 * 인증 관리자가 이메일로 계정을 찾을 때 호출하는 DB 조회 어댑터.
 * 반환한 비밀번호 해시와 활성 상태는 DaoAuthenticationProvider가 인증에 사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

	private final MemberRepository memberRepository;

	@Override
	public UserDetails loadUserByUsername(String username) {
		return memberRepository.findByEmail(EmailNormalizer.normalize(username))
			.map(MemberSecurityDTO::new)
			.orElseThrow(() -> new UsernameNotFoundException("Account not found"));
	}
}
