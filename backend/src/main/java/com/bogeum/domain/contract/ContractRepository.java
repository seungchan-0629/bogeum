package com.bogeum.domain.contract;

import org.springframework.data.jpa.repository.JpaRepository;
import com.bogeum.domain.contract.model.Contract;

/** AS 접수 시 임차인·매물·계약 기간을 확인할 계약을 조회한다. */
public interface ContractRepository extends JpaRepository<Contract, Long> {
}
