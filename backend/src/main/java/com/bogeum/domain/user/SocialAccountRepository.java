package com.bogeum.domain.user;

import com.bogeum.common.enums.SocialProvider;
import com.bogeum.domain.user.model.SocialAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

	Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

	boolean existsByUserUserIdAndProvider(Long userId, SocialProvider provider);
}
