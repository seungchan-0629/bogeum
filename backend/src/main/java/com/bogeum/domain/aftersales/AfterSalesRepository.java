package com.bogeum.domain.aftersales;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.bogeum.domain.aftersales.model.AfterSalesRequest;

/** AS 접수 저장과 매물의 현 소유자 조건 조회를 담당한다 (AS_001, AS_002). */
public interface AfterSalesRepository extends JpaRepository<AfterSalesRequest, Long> {
	// 매물·임차인은 목록 DTO에서 사용하므로 함께 가져온다. 소유자 제한은 DB 조회에 포함한다.
	@EntityGraph(attributePaths = {"property", "tenant"})
	Page<AfterSalesRequest> findAllByProperty_Landlord_UserId(Long landlordUserId, Pageable pageable);

	// 타인 소유 접수도 없는 접수처럼 처리해 접수 번호만으로 존재 여부를 알 수 없게 한다.
	@EntityGraph(attributePaths = {"property", "tenant"})
	Optional<AfterSalesRequest> findByAsRequestIdAndProperty_Landlord_UserId(Long asRequestId, Long landlordUserId);

	// 쓰기 작업만 같은 접수 행을 잠가 완료 검사와 변경이 서로 엇갈리지 않게 한다 (AS_003).
	// 조회와 동일한 현 소유자 조건을 사용하며 기존 읽기 전용 API에는 잠금을 적용하지 않는다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<AfterSalesRequest> findForUpdateByAsRequestIdAndProperty_Landlord_UserId(Long asRequestId, Long landlordUserId);
}
