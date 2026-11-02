package com.bogeum.domain.settlement;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import com.bogeum.common.enums.SettlementStatus;
import com.bogeum.domain.settlement.model.Settlement;
import jakarta.persistence.LockModeType;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Settlement> findByRentPaymentRentPaymentId(Long rentPaymentId);

	@Query("select s.rentPayment.rentPaymentId from Settlement s where s.settlementStatus = :status "
		+ "and exists (select a from SettlementAccount a where a.landlord = s.landlord) "
		+ "and exists (select p from PaymentAttempt p where p.rentPayment = s.rentPayment "
		+ "and p.attemptStatus = com.bogeum.common.enums.PaymentAttemptStatus.SUCCEEDED and p.bankTranId is not null) "
		+ "order by s.settlementId")
	List<Long> findRentPaymentIdsByStatus(SettlementStatus status, Pageable pageable);
}
