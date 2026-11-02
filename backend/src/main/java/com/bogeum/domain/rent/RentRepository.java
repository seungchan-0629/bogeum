package com.bogeum.domain.rent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import com.bogeum.common.enums.RentPaymentStatus;
import com.bogeum.domain.rent.model.RentPayment;
import jakarta.persistence.LockModeType;

public interface RentRepository extends JpaRepository<RentPayment, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RentPayment r where r.rentPaymentId = :rentPaymentId and r.contract.tenant.userId = :tenantId")
	Optional<RentPayment> findLockedByIdAndTenantId(Long rentPaymentId, Long tenantId);

	List<RentPayment> findByContractTenantUserIdOrderByDueAtDesc(Long tenantId);

	Optional<RentPayment> findByRentPaymentIdAndContractTenantUserId(Long rentPaymentId, Long tenantId);

	@Query("select r.rentPaymentId as rentPaymentId, r.contract.tenant.userId as tenantId from RentPayment r "
		+ "where r.rentPaymentStatus = :status and r.dueAt <= :now order by r.dueAt")
	List<DuePayment> findDuePayments(RentPaymentStatus status, LocalDateTime now, Pageable pageable);

	interface DuePayment {
		Long getRentPaymentId();
		Long getTenantId();
	}
}
