package com.bogeum.domain.rent.model;

import java.time.LocalDateTime;
import java.time.ZoneId;
import com.bogeum.common.enums.PaymentAttemptStatus;
import jakarta.persistence.*;
import lombok.*;

/** 은행 원거래 증빙에 근거한 관리자 확정 기록. 입력 증빙에는 토큰·계좌번호를 넣지 않는다. */
@Entity
@Table(name = "collection_resolution")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionResolution {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long collectionResolutionId;
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "payment_attempt_id", nullable = false, unique = true, updatable = false)
	private PaymentAttempt paymentAttempt;
	@Column(nullable = false, updatable = false)
	private Long adminUserId;
	@Column(nullable = false, length = 200, updatable = false)
	private String evidenceReference;
	@Enumerated(EnumType.STRING)
	@org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20, updatable = false)
	private PaymentAttemptStatus previousStatus;
	@Column(nullable = false, updatable = false)
	private boolean succeeded;
	@Builder.Default
	@Column(nullable = false, updatable = false)
	private LocalDateTime resolvedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
}
