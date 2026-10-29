package com.bogeum.domain.agent.model;

import java.time.LocalDateTime;

import com.bogeum.common.enums.ApprovalStatus;
import com.bogeum.domain.user.model.UserDto.AgreementsRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 중개사 기능의 요청·응답 DTO.
 */
public class AgentDto {

	/** 중개사 회원가입 요청. */
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SignupRequest {

		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식으로 입력해 주세요.")
		@Size(max = 255, message = "이메일은 255자 이하로 입력해 주세요.")
		private String email;

		@NotBlank(message = "비밀번호를 입력해 주세요.")
		private String password;

		@NotBlank(message = "대표자명을 입력해 주세요.")
		@Size(max = 50, message = "대표자명은 50자 이하로 입력해 주세요.")
		private String representativeName;

		@NotBlank(message = "전화번호를 입력해 주세요.")
		@Size(max = 20, message = "전화번호는 20자 이하로 입력해 주세요.")
		private String phone;

		@NotBlank(message = "중개사무소명을 입력해 주세요.")
		@Size(max = 100, message = "중개사무소명은 100자 이하로 입력해 주세요.")
		private String officeName;

		@NotBlank(message = "중개사무소 주소를 입력해 주세요.")
		@Size(max = 255, message = "중개사무소 주소는 255자 이하로 입력해 주세요.")
		private String officeAddress;

		@Valid
		@NotNull(message = "필수 항목에 동의해 주세요.")
		private AgreementsRequest agreements;
	}

	/** 가입 완료 후 반환할 중개사 식별자와 승인·서류 제출 정보. */
	@Getter
	@Builder
	public static class SignupResponse {

		private Long userId;
		private Long agentId;
		private ApprovalStatus approvalStatus;
		private LocalDateTime documentDueAt;

		/** 저장된 중개사에서 가입 응답에 필요한 값만 가져온다. */
		public static SignupResponse from(Agent agent) {
			return SignupResponse.builder()
				.userId(agent.getUser().getUserId())
				.agentId(agent.getAgentProfileId())
				.approvalStatus(agent.getApprovalStatus())
				.documentDueAt(agent.getDocumentDueAt())
				.build();
		}
	}

	/** 중개사 프로필 응답. 비밀번호 해시와 내부 연관관계는 포함하지 않는다. */
	@Getter
	@Builder
	public static class ProfileResponse {

		private Long userId;
		private Long agentId;
		private String email;
		private String representativeName;
		private String phone;
		private String officeName;
		private String officeAddress;
		private ApprovalStatus approvalStatus;
		private String rejectReason;
		private LocalDateTime documentDueAt;
		private LocalDateTime createdAt;
		private boolean demoApprovalAvailable;

		/** 중개사 엔티티를 데모 승인 기능이 비활성화된 프로필 응답으로 변환한다. */
		public static ProfileResponse from(Agent agent) {
			return from(agent, false);
		}

		/** 중개사 정보와 전달받은 데모 승인 기능 표시 여부를 응답에 매핑한다. */
		public static ProfileResponse from(Agent agent, boolean demoApprovalAvailable) {
			var user = agent.getUser();
			return ProfileResponse.builder()
				.userId(user.getUserId())
				.agentId(agent.getAgentProfileId())
				.email(user.getEmail())
				.representativeName(user.getName())
				.phone(user.getPhone())
				.officeName(agent.getOfficeName())
				.officeAddress(agent.getOfficeAddress())
				.approvalStatus(agent.getApprovalStatus())
				.rejectReason(agent.getRejectReason())
				.documentDueAt(agent.getDocumentDueAt())
				.createdAt(agent.getCreatedAt())
				.demoApprovalAvailable(demoApprovalAvailable)
				.build();
		}
	}
}
