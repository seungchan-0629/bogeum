package com.bogeum.domain.agent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.bogeum.common.enums.ApprovalStatus;
import com.bogeum.domain.agent.model.Agent;

public interface AgentRepository extends JpaRepository<Agent, Long> {
	@EntityGraph(attributePaths = "user")
	Optional<Agent> findByUser_UserId(Long userId);
	boolean existsByUser_UserIdAndApprovalStatus(Long userId, ApprovalStatus approvalStatus);
	@EntityGraph(attributePaths = "user")
	Optional<Agent> findByAgentProfileIdAndApprovalStatus(Long agentProfileId, ApprovalStatus approvalStatus);
	@EntityGraph(attributePaths = "user")
	List<Agent> findAllByApprovalStatus(ApprovalStatus approvalStatus);
}
