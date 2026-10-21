package com.bogeum.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.domain.Contract;

public interface ContractRepository extends JpaRepository<Contract, Long> {
}
