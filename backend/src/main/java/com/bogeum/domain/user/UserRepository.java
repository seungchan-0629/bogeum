package com.bogeum.domain.user;

import com.bogeum.domain.user.model.User;
import com.bogeum.utils.JwtUtil.RefreshToken;
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

	public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
		@Query("select r from RefreshToken r join fetch r.user where r.sessionKey = :sessionKey")
		Optional<RefreshToken> findWithUser(@Param("sessionKey") String sessionKey);

		// 같은 Refresh Token을 동시에 사용해 두 번 갱신하지 못하도록 직렬화한다.
		@Lock(LockModeType.PESSIMISTIC_WRITE)
		@Query("select r from RefreshToken r join fetch r.user where r.sessionKey = :sessionKey")
		Optional<RefreshToken> findForUpdate(@Param("sessionKey") String sessionKey);
	}
}
