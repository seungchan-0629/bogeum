package com.bogeum.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.domain.Member;

/**
 * 공통 회원 계정의 DB 접근 창구. Spring Data JPA가 메서드 이름을 해석해 이메일 조회 쿼리를 생성한다.
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByEmail(String email);

	boolean existsByEmail(String email);
}
