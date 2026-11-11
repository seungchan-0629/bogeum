package com.bogeum.domain.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.domain.contract.model.Contract;

public interface ContractRepository extends JpaRepository<Contract, Long> {
}
