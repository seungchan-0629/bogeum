package com.bogeum.domain.rent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.RentPaymentStatus;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.contract.ContractRepository;
import com.bogeum.domain.contract.model.Contract;
import com.bogeum.domain.openbanking.OpenBankingException;
import com.bogeum.domain.rent.model.RentPayment;
import com.bogeum.domain.user.model.User;

/** 은행 호출 없이 계약 기간·금액 검증과 월세 회차 생성 정책을 확인한다. */
@ExtendWith(MockitoExtension.class)
class RentServiceTest {

	private static final Long CONTRACT_ID = 11L;
	@Mock private ContractRepository contracts;
	@Mock private RentRepository rents;
	@InjectMocks private RentService service;

	@Test
	@DisplayName("31일 납부는 윤년 2월 말일로 보정하고 한국 시각 오전 9시에 생성한다")
	void generatesMonthEndScheduleIncludingExactBoundaries() {
		Contract contract = validContract().paymentDay(31)
			.startAt(LocalDateTime.of(2028, 1, 31, 9, 0))
			.endAt(LocalDateTime.of(2028, 3, 31, 9, 0)).build();
		prepare(contract);
		when(rents.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

		List<RentPayment> generated = service.generatePayments(CONTRACT_ID);

		assertThat(generated).extracting(RentPayment::getDueAt).containsExactly(
			LocalDateTime.of(2028, 1, 31, 9, 0), LocalDateTime.of(2028, 2, 29, 9, 0),
			LocalDateTime.of(2028, 3, 31, 9, 0));
		assertThat(generated).extracting(RentPayment::getSeq).containsExactly(1, 2, 3);
		assertThat(generated).allSatisfy(rent -> {
			assertThat(rent.getContract()).isSameAs(contract);
			assertThat(rent.getAmount()).isEqualByComparingTo("650000");
			assertThat(rent.getRentPaymentStatus()).isEqualTo(RentPaymentStatus.SCHEDULED);
		});
	}

	@Test
	@DisplayName("기간 밖의 첫달·마지막달 납부일은 제외하고 일할 월세를 만들지 않는다")
	void excludesDueTimesOutsideContractPeriod() {
		prepare(validContract().paymentDay(15).startAt(LocalDateTime.of(2027, 1, 20, 0, 0))
			.endAt(LocalDateTime.of(2027, 3, 14, 23, 59)).build());
		when(rents.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

		assertThat(service.generatePayments(CONTRACT_ID)).extracting(RentPayment::getDueAt)
			.containsExactly(LocalDateTime.of(2027, 2, 15, 9, 0));
	}

	@Test
	@DisplayName("전세의 0원 월세는 납부 회차를 생성하지 않는다")
	void doesNotGenerateJeonsePayments() {
		prepare(validContract().leaseType(LeaseType.JEONSE).monthlyRent(BigDecimal.ZERO).paymentDay(null).build());

		assertThat(service.generatePayments(CONTRACT_ID)).isEmpty();

		verify(rents, never()).saveAllAndFlush(anyList());
	}

	@ParameterizedTest
	@MethodSource("invalidContracts")
	@DisplayName("잘못된 계약 기간·월세·납부일·계약 당사자는 회차 생성 전에 거부한다")
	void rejectsInvalidContract(Contract contract) {
		prepare(contract);

		assertError(ErrorCode.INVALID_RENT_SCHEDULE, () -> service.generatePayments(CONTRACT_ID));

		verify(rents, never()).saveAllAndFlush(anyList());
	}

	@Test
	@DisplayName("이미 생성한 회차는 계약 정보가 바뀌어도 그대로 반환한다")
	void keepsExistingScheduleSnapshot() {
		var existing = RentPayment.builder().seq(1).amount(new BigDecimal("650000"))
			.dueAt(LocalDateTime.of(2027, 1, 15, 9, 0)).build();
		when(contracts.findLockedById(CONTRACT_ID)).thenReturn(Optional.of(
			validContract().monthlyRent(new BigDecimal("900000")).paymentDay(20).build()));
		when(rents.findByContractContractIdOrderBySeqAsc(CONTRACT_ID)).thenReturn(List.of(existing));

		assertThat(service.generatePayments(CONTRACT_ID)).containsExactly(existing);
		assertThat(existing.getAmount()).isEqualByComparingTo("650000");
		assertThat(existing.getDueAt()).isEqualTo(LocalDateTime.of(2027, 1, 15, 9, 0));
		verify(rents, never()).saveAllAndFlush(anyList());
	}

	@Test
	@DisplayName("없는 계약은 생성과 조회 모두 404 도메인 오류로 반환한다")
	void rejectsMissingContract() {
		when(contracts.findLockedById(CONTRACT_ID)).thenReturn(Optional.empty());
		when(contracts.existsById(CONTRACT_ID)).thenReturn(false);

		assertError(ErrorCode.CONTRACT_NOT_FOUND, () -> service.generatePayments(CONTRACT_ID));
		assertError(ErrorCode.CONTRACT_NOT_FOUND, () -> service.findContractPayments(CONTRACT_ID));
		verify(rents, never()).findByContractContractIdOrderBySeqAsc(CONTRACT_ID);
	}

	@Test
	@DisplayName("회차 목록 조회는 계약 존재만 확인하고 생성하지 않는다")
	void listsWithoutGenerating() {
		var existing = List.of(RentPayment.builder().seq(1).build());
		when(contracts.existsById(CONTRACT_ID)).thenReturn(true);
		when(rents.findByContractContractIdOrderBySeqAsc(CONTRACT_ID)).thenReturn(existing);

		assertThat(service.findContractPayments(CONTRACT_ID)).isSameAs(existing);

		verify(contracts, never()).findLockedById(CONTRACT_ID);
		verify(rents, never()).saveAllAndFlush(anyList());
	}

	private void prepare(Contract contract) {
		when(contracts.findLockedById(CONTRACT_ID)).thenReturn(Optional.of(contract));
		when(rents.findByContractContractIdOrderBySeqAsc(CONTRACT_ID)).thenReturn(List.of());
	}

	private static Stream<Contract> invalidContracts() {
		return Stream.of(
			validContract().monthlyRent(null).build(),
			validContract().monthlyRent(BigDecimal.ZERO).build(),
			validContract().monthlyRent(new BigDecimal("-1")).build(),
			validContract().monthlyRent(new BigDecimal("1.5")).build(),
			validContract().monthlyRent(new BigDecimal("1000000000000")).build(),
			validContract().paymentDay(null).build(),
			validContract().paymentDay(0).build(),
			validContract().paymentDay(32).build(),
			validContract().startAt(null).build(),
			validContract().endAt(null).build(),
			validContract().endAt(LocalDateTime.of(2026, 12, 31, 0, 0)).build(),
			validContract().leaseType(null).build(),
			validContract().leaseType(LeaseType.JEONSE).monthlyRent(BigDecimal.ONE).build(),
			validContract().tenant(null).build(),
			validContract().landlord(User.builder().build()).build());
	}

	private static Contract.ContractBuilder validContract() {
		return Contract.builder().contractId(CONTRACT_ID).leaseType(LeaseType.MONTHLY)
			.tenant(User.builder().userId(1L).build()).landlord(User.builder().userId(2L).build())
			.monthlyRent(new BigDecimal("650000")).paymentDay(15)
			.startAt(LocalDateTime.of(2027, 1, 1, 0, 0)).endAt(LocalDateTime.of(2027, 3, 31, 23, 59));
	}

	private static void assertError(ErrorCode code, Runnable action) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(OpenBankingException.class,
			exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
	}
}
