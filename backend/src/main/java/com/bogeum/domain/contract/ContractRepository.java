package com.bogeum.domain.contract;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.bogeum.domain.contract.model.Contract;

/** AS 접수 시 임차인·매물·계약 기간을 확인할 계약을 조회한다. */
public interface ContractRepository extends JpaRepository<Contract, Long> {

	// 중개 이력에 매물 주소와 임대인·임차인 이름을 보여 줘서 함께 가져온다 (AGENT_003)
	@EntityGraph(attributePaths = {"property", "landlord", "tenant"})
	Page<Contract> findAllByAgent_UserId(Long agentUserId, Pageable pageable);
}
