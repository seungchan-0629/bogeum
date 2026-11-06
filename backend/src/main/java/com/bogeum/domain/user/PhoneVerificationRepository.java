package com.bogeum.domain.user;

import com.bogeum.domain.user.model.PhoneVerification;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** 같은 인증에 대한 동시 요청은 DB 행 잠금으로 순서대로 처리한다. */
public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<PhoneVerification> findByPhone(String phone);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<PhoneVerification> findByVerificationId(String verificationId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<PhoneVerification> findByVerificationToken(String verificationToken);
}
