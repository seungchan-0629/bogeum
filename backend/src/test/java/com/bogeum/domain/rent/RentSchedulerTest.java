package com.bogeum.domain.rent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.bogeum.common.enums.RentPaymentStatus;
import com.bogeum.common.enums.SettlementStatus;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.openbanking.OpenBankingException;
import com.bogeum.domain.rent.RentRepository.DuePayment;
import com.bogeum.domain.settlement.SettlementRepository;
import com.bogeum.domain.settlement.SettlementService;

/** 수납·지급 앞 페이지의 보류 거래가 뒤 페이지의 정상 거래를 굶기지 않는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class RentSchedulerTest {
	@Mock private RentRepository rents;
	@Mock private RentService rentService;
	@Mock private SettlementRepository settlements;
	@Mock private SettlementService settlementService;
	private RentScheduler scheduler;

	@BeforeEach
	void createScheduler() {
		scheduler = new RentScheduler(rents, rentService, settlements, settlementService);
	}

	@Test
	void reaches101stPaymentEvenWhenFirst100AreRejectedAndStillProcessesPendingPayouts() {
		List<DuePayment> blocked = LongStream.rangeClosed(1, 100).mapToObj(this::due).toList();
		when(rents.findDuePaymentsAfter(eq(RentPaymentStatus.SCHEDULED), any(LocalDateTime.class), eq(0L), any(Pageable.class)))
			.thenReturn(blocked);
		when(rents.findDuePaymentsAfter(eq(RentPaymentStatus.SCHEDULED), any(LocalDateTime.class), eq(100L), any(Pageable.class)))
			.thenReturn(List.of(due(101L)));
		for (long id = 1; id <= 100; id++) {
			when(rentService.pay(id, id + 1000L))
				.thenThrow(OpenBankingException.of(ErrorCode.PAYMENT_ACCOUNT_REQUIRED));
		}
		when(settlements.findRentPaymentIdsByStatusAfter(eq(SettlementStatus.PENDING), eq(0L), any(Pageable.class)))
			.thenReturn(List.of(900L));

		scheduler.collectDuePayments();

		for (long id = 1; id <= 101; id++) {
			verify(rentService).pay(id, id + 1000L);
		}
		var pages = ArgumentCaptor.forClass(Pageable.class);
		var cursors = ArgumentCaptor.forClass(Long.class);
		verify(rents, times(2)).findDuePaymentsAfter(eq(RentPaymentStatus.SCHEDULED),
			any(LocalDateTime.class), cursors.capture(), pages.capture());
		assertEquals(List.of(0L, 100L), cursors.getAllValues());
		assertEquals(List.of(PageRequest.of(0, 100), PageRequest.of(0, 100)), pages.getAllValues());
		verify(settlementService).trySettle(900L);
	}

	@Test
	void usesLastSeenIdInsteadOfRowCountAsTheNextPageCursor() {
		List<DuePayment> firstPage = LongStream.rangeClosed(1, 100).map(value -> value * 10).mapToObj(this::due).toList();
		when(rents.findDuePaymentsAfter(eq(RentPaymentStatus.SCHEDULED), any(LocalDateTime.class), eq(0L), any(Pageable.class)))
			.thenReturn(firstPage);
		when(rents.findDuePaymentsAfter(eq(RentPaymentStatus.SCHEDULED), any(LocalDateTime.class), eq(1000L), any(Pageable.class)))
			.thenReturn(List.of(due(2000L)));
		when(settlements.findRentPaymentIdsByStatusAfter(eq(SettlementStatus.PENDING), eq(0L), any(Pageable.class)))
			.thenReturn(List.of());

		scheduler.collectDuePayments();

		verify(rentService).pay(2000L, 3000L);
		var cursors = ArgumentCaptor.forClass(Long.class);
		verify(rents, times(2)).findDuePaymentsAfter(eq(RentPaymentStatus.SCHEDULED),
			any(LocalDateTime.class), cursors.capture(), any(Pageable.class));
		assertEquals(List.of(0L, 1000L), cursors.getAllValues());
	}

	@Test
	void emptyCollectionPageStopsPagingAndStillProcessesPendingPayouts() {
		when(rents.findDuePaymentsAfter(eq(RentPaymentStatus.SCHEDULED), any(LocalDateTime.class), eq(0L), any(Pageable.class)))
			.thenReturn(List.of());
		when(settlements.findRentPaymentIdsByStatusAfter(eq(SettlementStatus.PENDING), eq(0L), any(Pageable.class)))
			.thenReturn(List.of(901L, 902L));

		scheduler.collectDuePayments();

		verify(rents).findDuePaymentsAfter(eq(RentPaymentStatus.SCHEDULED), any(LocalDateTime.class), eq(0L), eq(PageRequest.of(0, 100)));
		verifyNoInteractions(rentService);
		verify(settlementService).trySettle(901L);
		verify(settlementService).trySettle(902L);
	}

	@Test
	void reaches101stPayoutEvenWhenFirst100RemainPendingAfterTrySettle() {
		when(rents.findDuePaymentsAfter(eq(RentPaymentStatus.SCHEDULED), any(LocalDateTime.class), eq(0L), any(Pageable.class)))
			.thenReturn(List.of());
		// trySettle는 내부 예외를 삼킨다. 앞100건은 호출 후에도 PENDING으로 남는 상황이다.
		when(settlements.findRentPaymentIdsByStatusAfter(eq(SettlementStatus.PENDING), eq(0L), any(Pageable.class)))
			.thenReturn(LongStream.rangeClosed(1, 100).boxed().toList());
		when(settlements.findRentPaymentIdsByStatusAfter(eq(SettlementStatus.PENDING), eq(100L), any(Pageable.class)))
			.thenReturn(List.of(101L));

		scheduler.collectDuePayments();

		for (long id = 1; id <= 101; id++) {
			verify(settlementService).trySettle(id);
		}
		var cursors = ArgumentCaptor.forClass(Long.class);
		var pages = ArgumentCaptor.forClass(Pageable.class);
		verify(settlements, times(2)).findRentPaymentIdsByStatusAfter(eq(SettlementStatus.PENDING), cursors.capture(), pages.capture());
		assertEquals(List.of(0L, 100L), cursors.getAllValues());
		assertEquals(List.of(PageRequest.of(0, 100), PageRequest.of(0, 100)), pages.getAllValues());
		verifyNoInteractions(rentService);
	}

	private DuePayment due(long id) {
		return new DuePayment() {
			@Override public Long getRentPaymentId() { return id; }
			@Override public Long getTenantId() { return id + 1000; }
		};
	}
}
