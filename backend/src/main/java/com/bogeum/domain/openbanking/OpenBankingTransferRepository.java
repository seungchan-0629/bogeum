package com.bogeum.domain.openbanking;

import com.bogeum.domain.openbanking.model.OpenBankingTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.time.LocalDateTime;

public interface OpenBankingTransferRepository extends JpaRepository<OpenBankingTransfer,Long> {
	@Query("""
		select t from OpenBankingTransfer t
		join fetch t.contract c join fetch c.property p
		where c.tenant.userId = :userId
		and t.transfer_type = com.bogeum.domain.openbanking.TransferType.WITHDRAW
		and (:contractId is null or c.contractId = :contractId)
		and (:propertyId is null or p.propertyId = :propertyId)
		and (:fromAt is null or t.requestedAt >= :fromAt)
		and (:toAt is null or t.requestedAt < :toAt)
		and (:cursor is null or t.openBankingTransferId < :cursor)
		order by t.openBankingTransferId desc
		""")
	Slice<OpenBankingTransfer> findWithdrawals(Long userId, Long contractId, Long propertyId,
		LocalDateTime fromAt, LocalDateTime toAt, Long cursor, Pageable pageable);

	@Query("""
		select t from OpenBankingTransfer t
		join fetch t.contract c join fetch c.property p
		where c.landlord.userId = :userId
		and t.transfer_type = com.bogeum.domain.openbanking.TransferType.REMITTANCE
		and (:contractId is null or c.contractId = :contractId)
		and (:propertyId is null or p.propertyId = :propertyId)
		and (:fromAt is null or t.requestedAt >= :fromAt)
		and (:toAt is null or t.requestedAt < :toAt)
		and (:cursor is null or t.openBankingTransferId < :cursor)
		order by t.openBankingTransferId desc
		""")
	Slice<OpenBankingTransfer> findRemittances(Long userId, Long contractId, Long propertyId,
		LocalDateTime fromAt, LocalDateTime toAt, Long cursor, Pageable pageable);
}
