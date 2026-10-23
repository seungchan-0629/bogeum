package com.bogeum.repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.bogeum.domain.SocialAccount;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
	@EntityGraph(attributePaths = "member")
	Optional<SocialAccount> findByProviderAndProviderUserId(String provider, String providerUserId);
	boolean existsByMemberIdAndProvider(Long memberId, String provider);
	List<SocialAccount> findAllByMemberIdOrderByProviderAsc(Long memberId);
}
