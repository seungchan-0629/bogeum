package com.bogeum.domain.aftersales;

import org.springframework.data.jpa.repository.JpaRepository;
import com.bogeum.domain.aftersales.model.AfterSalesRequest;

/** 계약에서 확인한 매물·임차인의 AS 접수 내역을 저장한다 (AS_001). */
public interface AfterSalesRepository extends JpaRepository<AfterSalesRequest, Long> {
}
