package com.bogeum.domain.openbanking.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.bogeum.domain.openbanking.model.OpenBankingConnection;

import jakarta.persistence.LockModeType;

public interface OpenBankingConnectionRepository extends JpaRepository<OpenBankingConnection, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<OpenBankingConnection> findByUserUserId(Long userId);
}
