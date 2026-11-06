package com.bogeum.domain.aftersales;

import org.springframework.data.jpa.repository.JpaRepository;
import com.bogeum.domain.aftersales.model.AfterSalesRequestImage;

/** AS 접수와 서버가 생성한 사진 저장 키의 관계를 저장한다. 파일 저장은 맡지 않는다. */
public interface AfterSalesRequestImageRepository extends JpaRepository<AfterSalesRequestImage, Long> {
}
