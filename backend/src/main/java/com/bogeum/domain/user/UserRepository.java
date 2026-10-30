package com.bogeum.domain.user;

import com.bogeum.domain.user.model.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 사용자 저장 및 로그인 아이디 조회를 담당한다. */
public interface UserRepository extends JpaRepository<User, Long> {

	/** 비밀번호는 조회 조건에 넣지 않고 서비스에서 해시와 비교한다. */
	Optional<User> findByEmail(String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.userId = :userId")
	Optional<User> findLockedById(@Param("userId") Long userId);
}
