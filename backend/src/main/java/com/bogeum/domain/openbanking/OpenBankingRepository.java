package com.bogeum.domain.openbanking;

import com.bogeum.domain.openbanking.model.OpenBanking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpenBankingRepository extends JpaRepository<OpenBanking,Long>
{
}
