package com.bogeum.domain.openbanking;

import com.bogeum.domain.openbanking.model.OpenBankingTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpenBankingTransferRepository extends JpaRepository<OpenBankingTransfer,Long> {
}
