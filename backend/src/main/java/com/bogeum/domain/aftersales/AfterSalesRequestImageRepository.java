package com.bogeum.domain.aftersales;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.bogeum.domain.aftersales.model.AfterSalesRequestImage;

/** AS 접수와 서버가 생성한 사진 저장 키의 관계를 저장한다. 파일 저장은 맡지 않는다. */
public interface AfterSalesRequestImageRepository extends JpaRepository<AfterSalesRequestImage, Long> {
	// 서비스에서 AS 소유 권한을 확인한 뒤 호출한다. 같은 등록 시각에도 사진 순서를 일정하게 유지한다.
	List<AfterSalesRequestImage> findAllByAsRequest_AsRequestIdOrderByCreatedAtAscAsRequestImageIdAsc(Long asRequestId);
}
