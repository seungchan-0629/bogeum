package com.bogeum.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.domain.ConsentRecord;

/**
 * 가입 트랜잭션에서 동의 이력을 저장하고, 회원 ID로 해당 회원의 이력을 조회한다.
 */
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

	List<ConsentRecord> findByMemberId(Long memberId);
}
