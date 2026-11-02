package com.bogeum.domain.settlement.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.bogeum.domain.settlement.model.SettlementAccount;

public interface SettlementAccountRepository extends JpaRepository<SettlementAccount, Long> {
	Optional<SettlementAccount> findByLandlordUserId(Long landlordId);
}
