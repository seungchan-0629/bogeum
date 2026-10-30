package com.bogeum.domain.rent.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.bogeum.domain.rent.model.PaymentAttempt;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
	Optional<PaymentAttempt> findFirstByRentPaymentRentPaymentIdOrderByAttemptNoDesc(Long rentPaymentId);
}
