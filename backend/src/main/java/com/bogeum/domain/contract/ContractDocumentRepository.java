package com.bogeum.domain.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.domain.contract.model.ContractDocument;

public interface ContractDocumentRepository extends JpaRepository<ContractDocument, Long> {
}
