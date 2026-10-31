package com.bogeum.domain.settlement;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import com.bogeum.common.enums.SettlementStatus;
import com.bogeum.domain.settlement.model.Settlement;
import com.bogeum.domain.settlement.model.SettlementAccount;
import com.bogeum.domain.settlement.model.SettlementAttempt;
import com.bogeum.domain.settlement.model.SettlementResolution;
import jakarta.persistence.LockModeType;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Settlement> findByRentPaymentRentPaymentId(Long rentPaymentId);

	@Query("select s.rentPayment.rentPaymentId from Settlement s where s.settlementStatus = :status "
		+ "and s.rentPayment.rentPaymentId > :afterId "
		+ "and exists (select a from SettlementAccount a where a.landlord = s.landlord and a.verifiedAt is not null) "
		+ "and exists (select p from PaymentAttempt p where p.rentPayment = s.rentPayment "
		+ "and p.attemptStatus = com.bogeum.common.enums.PaymentAttemptStatus.SUCCEEDED and p.bankTranId is not null "
		+ "and p.fintechUseNumSnapshot is not null and p.tenantNameSnapshot is not null and p.tenantUserIdSnapshot is not null) "
		+ "order by s.rentPayment.rentPaymentId")
	List<Long> findRentPaymentIdsByStatusAfter(SettlementStatus status, Long afterId, Pageable pageable);

	interface SettlementAccountRepository extends JpaRepository<SettlementAccount, Long> {
		Optional<SettlementAccount> findByLandlordUserId(Long landlordId);
	}

	interface SettlementAttemptRepository extends JpaRepository<SettlementAttempt, Long> {
		Optional<SettlementAttempt> findFirstBySettlementSettlementIdOrderByAttemptNoDesc(Long settlementId);

		List<SettlementAttempt> findBySettlementSettlementIdOrderByAttemptNoAsc(Long settlementId);
	}

	interface SettlementResolutionRepository extends JpaRepository<SettlementResolution, Long> {
	}
}
