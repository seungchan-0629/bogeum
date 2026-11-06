package com.bogeum.domain.openbanking;

import com.bogeum.domain.openbanking.model.InstitutionToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionTokenRepository extends JpaRepository<InstitutionToken, Long> {
}
