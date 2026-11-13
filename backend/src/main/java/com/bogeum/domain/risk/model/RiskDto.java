package com.bogeum.domain.risk.model;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;

import com.bogeum.common.enums.RiskReason;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 위험정보 등록 요청과 조회 응답. 저장 키와 불필요한 개인정보는 노출하지 않는다. */
public class RiskDto {

	/** 사용자 위험정보 등록 요청. 등록자·상태·증빙 키는 요청으로 지정하지 않는다. */
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class UserRegReq {
		@NotNull(message = "등록할 회원 번호를 입력해 주세요.")
		@Positive(message = "회원 번호는 양수로 입력해 주세요.")
		private Long userId;
		@NotNull(message = "위험 사유를 선택해 주세요.")
		private RiskReason reasonType;
		// 선택값이며, 기존 TEXT 컬럼의 UTF-8 바이트 상한은 서비스에서 검증한다.
		private String detail;
	}

	/** 위험정보 한 건에 대응하는 관리자 목록 항목. */
	@Getter
	@Builder
	public static class UserItemRes {
		private Long riskInfoId;
		private Long userId;
		private String name;
		private RiskUserType userType;
		private RiskReason reasonType;
		// 관리자에게 제공하는 상세 내용. 기록이 없으면 null이다.
		private String detail;
		// 정식 등록일이 아니라 위험정보 생성 시각이다.
		private LocalDateTime createdAt;
		// DB의 키 기록 여부이며 실제 파일 존재나 열람 가능 여부를 보장하지 않는다.
		private boolean hasEvidence;

		/** 사유 기준 분류와 목록에 필요한 값만 매핑한다. 증빙 저장소는 조회하지 않는다. */
		public static UserItemRes from(RiskInfo riskInfo) {
			String evidenceKey = riskInfo.getEvidenceFileKey();
			return UserItemRes.builder().riskInfoId(riskInfo.getRiskInfoId())
				.userId(riskInfo.getTargetUser().getUserId()).name(riskInfo.getTargetUser().getName())
				.userType(RiskUserType.fromReason(riskInfo.getReasonType())).reasonType(riskInfo.getReasonType())
				.detail(riskInfo.getDetail())
				.createdAt(riskInfo.getCreatedAt()).hasEvidence(evidenceKey != null && !evidenceKey.isBlank())
				.build();
		}
	}

	/** 위험정보 목록과 1부터 시작하는 페이지 정보. */
	@Getter
	@Builder
	public static class UserListRes {
		private List<UserItemRes> riskUsers;
		private int page;
		private int totalPages;
		private long totalElements;

		/** DB에서 페이징한 조회 결과를 목록 응답으로 변환한다. */
		public static UserListRes from(Page<RiskInfo> riskUsers) {
			return UserListRes.builder().riskUsers(riskUsers.map(UserItemRes::from).getContent())
				.page(riskUsers.getNumber() + 1).totalPages(riskUsers.getTotalPages())
				.totalElements(riskUsers.getTotalElements()).build();
		}
	}
}
