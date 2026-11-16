package com.bogeum.domain.rent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.domain.rent.model.RentPayment;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RentService {
	private static final int PAGE_SIZE = 5;
	private final RentRepository rentRepository;

	/** 저장된 모든 납부 상태를 조회한다. 자동 결제나 실패·연체 집계는 수행하지 않는다. */
	@Transactional(readOnly = true)
	public Page<RentPayment> readAdminPayments(Long tenantId, String keyword, int page) {
		if (tenantId != null && tenantId <= 0) {
			throw RentException.invalidRequest();
		}
		int pageIndex = Math.max(page, 1) - 1;
		if ((long) pageIndex * PAGE_SIZE > Integer.MAX_VALUE) {
			throw RentException.invalidRequest();
		}
		String normalizedKeyword = keyword == null ? "" : keyword.strip();
		return rentRepository.findAdminPayments(tenantId, normalizedKeyword,
			PageRequest.of(pageIndex, PAGE_SIZE));
	}
}
