package com.bogeum.domain.contract;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bogeum.domain.contract.model.Contract;

import jakarta.persistence.LockModeType;

public interface ContractRepository extends JpaRepository<Contract, Long> {

	/** 회차 생성 요청을 계약 단위로 직렬화한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Contract c where c.contractId = :contractId")
	Optional<Contract> findLockedById(@Param("contractId") Long contractId);
}
