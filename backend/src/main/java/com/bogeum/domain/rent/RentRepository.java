package com.bogeum.domain.rent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import com.bogeum.common.enums.PaymentMethodStatus;
import com.bogeum.common.enums.RentPaymentStatus;
import com.bogeum.domain.rent.model.PaymentAttempt;
import com.bogeum.domain.rent.model.PaymentMethod;
import com.bogeum.domain.rent.model.RentPayment;
import com.bogeum.domain.rent.model.CollectionResolution;
import jakarta.persistence.LockModeType;

public interface RentRepository extends JpaRepository<RentPayment, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RentPayment r where r.rentPaymentId = :rentPaymentId")
	Optional<RentPayment> findLockedById(Long rentPaymentId);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RentPayment r where r.rentPaymentId = :rentPaymentId and r.contract.tenant.userId = :tenantId")
	Optional<RentPayment> findLockedByIdAndTenantId(Long rentPaymentId, Long tenantId);

	@EntityGraph(attributePaths = "settlement")
	List<RentPayment> findByContractTenantUserIdOrderByDueAtDesc(Long tenantId);

	@EntityGraph(attributePaths = "settlement")
	List<RentPayment> findByContractContractIdOrderBySeqAsc(Long contractId);

	@EntityGraph(attributePaths = "settlement")
	Optional<RentPayment> findByRentPaymentIdAndContractTenantUserId(Long rentPaymentId, Long tenantId);

	@Query("select r.rentPaymentId as rentPaymentId, r.contract.tenant.userId as tenantId from RentPayment r "
		+ "where r.rentPaymentStatus = :status and r.dueAt <= :now and r.rentPaymentId > :afterId order by r.rentPaymentId")
	List<DuePayment> findDuePaymentsAfter(RentPaymentStatus status, LocalDateTime now, Long afterId, Pageable pageable);

	interface DuePayment {
		Long getRentPaymentId();
		Long getTenantId();
	}

	interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
		Optional<PaymentAttempt> findFirstByRentPaymentRentPaymentIdOrderByAttemptNoDesc(Long rentPaymentId);
		List<PaymentAttempt> findByRentPaymentRentPaymentIdOrderByAttemptNoAsc(Long rentPaymentId);
	}

	interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
		List<PaymentMethod> findByTenantUserIdAndPaymentMethodStatus(Long tenantId, PaymentMethodStatus status);
	}

	interface CollectionResolutionRepository extends JpaRepository<CollectionResolution, Long> {
	}
}
