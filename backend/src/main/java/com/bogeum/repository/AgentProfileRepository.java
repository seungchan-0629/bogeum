package com.bogeum.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.domain.AgentProfile;

/**
 * 중개사 프로필의 저장·조회를 담당한다. 프로필의 기본 키는 연결된 회원 ID이다.
 */
public interface AgentProfileRepository extends JpaRepository<AgentProfile, Long> {
}
