package com.bogeum.domain.rent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bogeum.domain.rent.model.RentPayment;

public interface RentRepository extends JpaRepository<RentPayment, Long> {
	/** to-one 관계만 함께 조회해 회차 단위 페이지와 임차인·매물 매핑을 유지한다. */
	@EntityGraph(attributePaths = {"contract.tenant", "contract.property"})
	@Query("""
		select p from RentPayment p
		join p.contract c join c.tenant t
		where (:tenantId is null or t.userId = :tenantId)
			and (:keyword = '' or locate(lower(:keyword), lower(t.name)) > 0)
		order by p.dueAt desc, p.rentPaymentId desc
		""")
	Page<RentPayment> findAdminPayments(@Param("tenantId") Long tenantId,
		@Param("keyword") String keyword, Pageable pageable);
}
