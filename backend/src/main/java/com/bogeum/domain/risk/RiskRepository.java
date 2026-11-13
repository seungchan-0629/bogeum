package com.bogeum.domain.risk;

import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bogeum.common.enums.RiskReason;
import com.bogeum.common.enums.RiskStatus;
import com.bogeum.common.enums.RiskTargetType;
import com.bogeum.domain.risk.model.RiskInfo;

public interface RiskRepository extends JpaRepository<RiskInfo, Long> {

	/** 위험정보 한 건을 한 행으로 조회하고, 검색어의 %, _도 일반 문자로 취급한다. */
	@EntityGraph(attributePaths = "targetUser")
	@Query("""
		select r from RiskInfo r join r.targetUser u
		where r.targetType = :targetType and r.riskStatus = :status
			and r.reasonType in :reasons
			and (:keyword = '' or locate(lower(:keyword), lower(u.name)) > 0)
		order by r.createdAt desc, r.riskInfoId desc
		""")
	Page<RiskInfo> findAdminRiskUsers(@Param("targetType") RiskTargetType targetType,
		@Param("status") RiskStatus status, @Param("reasons") Collection<RiskReason> reasons,
		@Param("keyword") String keyword, Pageable pageable);
}
