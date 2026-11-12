package com.bogeum.domain.agent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import com.bogeum.common.enums.ApprovalStatus;
import com.bogeum.domain.agent.model.Agent;

public interface AgentRepository extends JpaRepository<Agent, Long> {
	/** LIKE 대신 locate를 사용해 검색어의 %, _도 일반 문자로 취급한다. */
	@EntityGraph(attributePaths = "user")
	@Query("""
		select a from Agent a
		where :keyword = '' or locate(lower(:keyword), lower(a.officeName)) > 0
			or locate(lower(:keyword), lower(a.officeAddress)) > 0
		order by case when a.approvalStatus = :pending then 0 else 1 end,
			a.createdAt desc, a.agentProfileId desc
		""")
	Page<Agent> findAdminAgents(@Param("keyword") String keyword,
		@Param("pending") ApprovalStatus pending, Pageable pageable);

	@EntityGraph(attributePaths = "user")
	Optional<Agent> findByUser_UserId(Long userId);
	boolean existsByUser_UserIdAndApprovalStatus(Long userId, ApprovalStatus approvalStatus);
	@EntityGraph(attributePaths = "user")
	Optional<Agent> findByAgentProfileIdAndApprovalStatus(Long agentProfileId, ApprovalStatus approvalStatus);
	@EntityGraph(attributePaths = "user")
	List<Agent> findAllByApprovalStatus(ApprovalStatus approvalStatus);
}
