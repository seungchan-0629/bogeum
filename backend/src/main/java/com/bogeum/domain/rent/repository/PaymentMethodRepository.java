package com.bogeum.domain.rent.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.bogeum.common.enums.PaymentMethodStatus;
import com.bogeum.domain.rent.model.PaymentMethod;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
	List<PaymentMethod> findByTenantUserIdAndPaymentMethodStatus(Long tenantId, PaymentMethodStatus status);
}
