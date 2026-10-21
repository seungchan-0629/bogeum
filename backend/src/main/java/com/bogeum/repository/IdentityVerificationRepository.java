package com.bogeum.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bogeum.domain.IdentityVerification;

import jakarta.persistence.LockModeType;

/**
 * 본인 인증 증빙을 저장하고, 토큰 소비 시 동시 접근을 제어하며 조회한다.
 */
public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, Long> {

	// 가입 트랜잭션이 끝날 때까지 행 잠금을 유지하여 같은 토큰의 동시 소비를 막는다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select verification from IdentityVerification verification where verification.tokenHash = :hash")
	Optional<IdentityVerification> findForConsumption(@Param("hash") String hash);
}
