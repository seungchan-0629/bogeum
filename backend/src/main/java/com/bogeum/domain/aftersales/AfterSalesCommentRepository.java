package com.bogeum.domain.aftersales;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.bogeum.domain.aftersales.model.AfterSalesComment;

/** AS 댓글 저장·정렬 조회만 담당한다. 열람 권한은 호출하는 서비스가 확인한다 (AS_003). */
public interface AfterSalesCommentRepository extends JpaRepository<AfterSalesComment, Long> {
	// 댓글 DTO에서 사용할 작성자를 함께 가져오며, 같은 생성 시각에도 순서를 일정하게 유지한다.
	@EntityGraph(attributePaths = "author")
	List<AfterSalesComment> findAllByAsRequest_AsRequestIdOrderByCreatedAtAscAsCommentIdAsc(Long asRequestId);
}
