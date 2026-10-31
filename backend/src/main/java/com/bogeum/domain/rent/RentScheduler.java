package com.bogeum.domain.rent;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.LocalDateTime;
import java.time.ZoneId;
import com.bogeum.common.enums.RentPaymentStatus;
import com.bogeum.common.enums.SettlementStatus;
import com.bogeum.domain.settlement.SettlementRepository;
import com.bogeum.domain.settlement.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "openbanking", name = {"enabled", "scheduling-enabled"}, havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class RentScheduler {
	private final RentRepository rents;
	private final RentService rentService;
	private final SettlementRepository settlements;
	private final SettlementService settlementService;

	/** 기존 계약 흐름에서 생성한 납부 회차만 처리한다. UNKNOWN·PROCESSING은 재전송하지 않는다. */
	@Scheduled(fixedDelayString = "${openbanking.schedule-delay-ms:60000}")
	public void collectDuePayments() {
		// 실패한 앞 회차가 SCHEDULED로 남아도 이후 회차를 계속 처리한다.
		Long afterId = 0L;
		var now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
		while (true) {
			var batch = rents.findDuePaymentsAfter(RentPaymentStatus.SCHEDULED, now, afterId, PageRequest.of(0, 100));
			for (var due : batch) {
				try {
					rentService.pay(due.getRentPaymentId(), due.getTenantId());
				} catch (RuntimeException exception) {
					log.warn("월세 수납 확인 필요: rentPaymentId={}, error={}",
						due.getRentPaymentId(), exception.getClass().getSimpleName());
				}
			}
			if (batch.size() < 100) break;
			afterId = batch.get(batch.size() - 1).getRentPaymentId();
		}
		afterId = 0L;
		while (true) {
			var batch = settlements.findRentPaymentIdsByStatusAfter(SettlementStatus.PENDING, afterId, PageRequest.of(0, 100));
			for (Long rentPaymentId : batch) settlementService.trySettle(rentPaymentId);
			if (batch.size() < 100) break;
			afterId = batch.get(batch.size() - 1);
		}
	}
}
