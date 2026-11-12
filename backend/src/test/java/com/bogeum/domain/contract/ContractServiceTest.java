package com.bogeum.domain.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.UserStatus;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.common.exception.ImageException;
import com.bogeum.domain.chat.ChatRepository;
import com.bogeum.domain.chat.model.ChatRoom;
import com.bogeum.domain.contract.model.Contract;
import com.bogeum.domain.contract.model.ContractDocument;
import com.bogeum.domain.contract.model.ContractDto;
import com.bogeum.domain.property.PropertyException;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

/** 계약 당사자·금액·기간 규칙과 계약서 저장을 DB 없이 확인한다. */
@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

	private static final LocalDate START = LocalDate.now().plusDays(10);
	private static final LocalDate END = START.plusYears(2);

	@Mock
	private PropertyRepository propertyRepository;
	@Mock
	private ChatRepository chatRepository;
	@Mock
	private ContractRepository contractRepository;
	@Mock
	private ContractDocumentRepository contractDocumentRepository;
	@Mock
	private ObjectProvider<ContractDocumentStorage> storageProvider;
	@Mock
	private ContractDocumentStorage storage;
	@Mock
	private ImageProcessor imageProcessor;

	private ContractService contractService;
	private final User landlord = User.builder().userId(1L).name("박임대").email("park@example.com").build();
	private final User agent = User.builder().userId(101L).name("김중개").email("agent@example.com").build();
	private final User seip = tenant(2L, "최세입", "seip@example.com", UserStatus.ACTIVE);

	@BeforeEach
	void setUp() {
		contractService = new ContractService(propertyRepository, chatRepository, contractRepository,
			contractDocumentRepository, storageProvider, imageProcessor);
	}

	@AfterEach
	void clearSynchronization() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	@DisplayName("월세 계약을 등록하면 매물의 임대인·중개사·거래 유형으로 저장하고 매물을 계약 완료로 바꾼다")
	void createsMonthlyContract() {
		Property property = givenPropertyForUpdate(LeaseType.MONTHLY, PropertyStatus.ACTIVE);
		givenTenantChatRooms(seip);
		givenSavedContract();
		when(storageProvider.getIfAvailable()).thenReturn(storage);
		when(imageProcessor.process(any())).thenReturn(new byte[]{1});
		// 프록시 없는 단위 테스트라 트랜잭션 동기화를 직접 연다
		TransactionSynchronizationManager.initSynchronization();

		var response = contractService.createContract(101L,
			request(2L, "9500000", "600000", 25, LocalDate.now().minusDays(3)), List.of(photo(), photo()));

		var saved = ArgumentCaptor.forClass(Contract.class);
		verify(contractRepository).save(saved.capture());
		Contract contract = saved.getValue();
		assertEquals(landlord, contract.getLandlord());
		assertEquals(seip, contract.getTenant());
		assertEquals(agent, contract.getAgent());
		assertEquals(LeaseType.MONTHLY, contract.getLeaseType());
		assertEquals(new BigDecimal("9500000"), contract.getDeposit());
		assertEquals(new BigDecimal("600000"), contract.getMonthlyRent());
		assertEquals(25, contract.getPaymentDay());
		assertEquals(START.atStartOfDay(), contract.getStartAt());
		assertEquals(END.atStartOfDay(), contract.getEndAt());
		assertEquals(new BigDecimal("300000"), contract.getBrokerageFee());
		assertEquals(LocalDate.now().minusDays(3).atStartOfDay(), contract.getContractedAt());
		assertEquals(PropertyStatus.CONTRACTED, property.getPropertyStatus());

		var documents = ArgumentCaptor.forClass(ContractDocument.class);
		verify(contractDocumentRepository, times(2)).save(documents.capture());
		assertTrue(documents.getAllValues().stream().allMatch(document -> document.getContract() == contract
			&& document.getUploadedBy() == agent && document.getFileKey().startsWith("contract-documents/30/")
			&& document.getFileKey().endsWith(".jpg")));
		verify(storage, times(2)).write(anyString(), any());
		assertEquals(30L, response.getContractId());
		assertEquals(20L, response.getPropertyId());
		assertEquals(PropertyStatus.CONTRACTED, response.getPropertyStatus());
	}

	@Test
	@DisplayName("전세 계약은 월세를 비워 보내도 0원으로, 납부일은 비운 채로 저장한다")
	void createsJeonseContract() {
		givenPropertyForUpdate(LeaseType.JEONSE, PropertyStatus.ACTIVE);
		givenTenantChatRooms(seip);
		givenSavedContract();
		when(storageProvider.getIfAvailable()).thenReturn(storage);
		when(imageProcessor.process(any())).thenReturn(new byte[]{1});
		TransactionSynchronizationManager.initSynchronization();

		contractService.createContract(101L, request(2L, "300000000", null, null, LocalDate.now()), List.of(photo()));

		var saved = ArgumentCaptor.forClass(Contract.class);
		verify(contractRepository).save(saved.capture());
		assertEquals(BigDecimal.ZERO, saved.getValue().getMonthlyRent());
		assertNull(saved.getValue().getPaymentDay());
	}

	@Test
	@DisplayName("월세에 납부일이 없거나, 전세에 월세·납부일이 있거나, 전세금이 0이면 저장하지 않는다")
	void rejectsPriceNotMatchingLeaseType() {
		// 매물 상태가 먼저 계약 완료로 바뀌므로 요청마다 새 매물을 준다 (실제로는 롤백된다)
		for (ContractDto.ContractRequest monthly : List.of(
				request(2L, "9500000", "600000", null, LocalDate.now()),
				request(2L, "9500000", "0", 25, LocalDate.now()))) {
			givenPropertyForUpdate(LeaseType.MONTHLY, PropertyStatus.ACTIVE);
			assertContractError(ErrorCode.INVALID_CONTRACT_PRICE, monthly);
		}
		for (ContractDto.ContractRequest jeonse : List.of(
				request(2L, "300000000", "100000", null, LocalDate.now()),
				request(2L, "300000000", null, 25, LocalDate.now()),
				request(2L, "0", null, null, LocalDate.now()))) {
			givenPropertyForUpdate(LeaseType.JEONSE, PropertyStatus.ACTIVE);
			assertContractError(ErrorCode.INVALID_CONTRACT_PRICE, jeonse);
		}
		verifyNoInteractions(contractRepository, storage);
	}

	@Test
	@DisplayName("시작일이 종료일보다 앞이 아니면 저장하지 않는다")
	void rejectsInvalidPeriod() {
		givenPropertyForUpdate(LeaseType.MONTHLY, PropertyStatus.ACTIVE);
		var request = new ContractDto.ContractRequest(20L, 2L, new BigDecimal("9500000"), new BigDecimal("600000"), 25,
			END, START, LocalDate.now(), new BigDecimal("300000"));

		assertContractError(ErrorCode.INVALID_CONTRACT_PERIOD, request);
		verifyNoInteractions(contractRepository);
	}

	@Test
	@DisplayName("계약일이 미래이거나 계약 종료일 이후면 저장하지 않는다")
	void rejectsInvalidContractedAt() {
		for (LocalDate contractedAt : List.of(LocalDate.now().plusDays(1), END)) {
			givenPropertyForUpdate(LeaseType.MONTHLY, PropertyStatus.ACTIVE);
			assertContractError(ErrorCode.INVALID_CONTRACTED_AT, request(2L, "9500000", "600000", 25, contractedAt));
		}
		verifyNoInteractions(contractRepository);
	}

	@Test
	@DisplayName("이 매물로 상담한 임차인 후보가 아니면 저장하지 않는다")
	void rejectsTenantOutsideCandidates() {
		givenPropertyForUpdate(LeaseType.MONTHLY, PropertyStatus.ACTIVE);
		givenTenantChatRooms(seip);

		assertContractError(ErrorCode.INVALID_CONTRACT_TENANT, request(99L, "9500000", "600000", 25, LocalDate.now()));
		verifyNoInteractions(contractRepository);
	}

	@Test
	@DisplayName("공개 중이 아닌 매물(공개 전·이미 계약)은 계약을 등록할 수 없다")
	void rejectsPropertyNotActive() {
		for (PropertyStatus status : List.of(PropertyStatus.REQUESTED, PropertyStatus.CONTRACTED)) {
			givenPropertyForUpdate(LeaseType.MONTHLY, status);
			var exception = assertThrows(PropertyException.class, () -> contractService.createContract(101L,
				request(2L, "9500000", "600000", 25, LocalDate.now()), List.of(photo())));
			assertEquals(ErrorCode.PROPERTY_NOT_CONTRACTABLE, exception.getErrorCode());
		}
		verifyNoInteractions(chatRepository, contractRepository, storage);
	}

	@Test
	@DisplayName("없거나 다른 중개사가 맡은 매물은 계약을 등록할 수 없다")
	void rejectsUnassignedPropertyOnCreate() {
		when(propertyRepository.findForUpdateByPropertyIdAndAgent_UserId(20L, 999L)).thenReturn(Optional.empty());

		var exception = assertThrows(PropertyException.class, () -> contractService.createContract(999L,
			request(2L, "9500000", "600000", 25, LocalDate.now()), List.of(photo())));

		assertEquals(ErrorCode.PROPERTY_NOT_FOUND, exception.getErrorCode());
		verifyNoInteractions(contractRepository);
	}

	@Test
	@DisplayName("계약서 이미지가 없거나 10장을 넘으면 저장소에 올리지 않고 거절한다")
	void rejectsNoDocumentsOrOverLimit() {
		for (List<MultipartFile> images : java.util.Arrays.<List<MultipartFile>>asList(
				null, List.of(), java.util.Collections.nCopies(11, photo()))) {
			givenPropertyForUpdate(LeaseType.MONTHLY, PropertyStatus.ACTIVE);
			givenTenantChatRooms(seip);
			var exception = assertThrows(ContractException.class, () -> contractService.createContract(101L,
				request(2L, "9500000", "600000", 25, LocalDate.now()), images));
			assertEquals(ErrorCode.INVALID_IMAGE, exception.getErrorCode());
		}
		verifyNoInteractions(imageProcessor, storage, contractRepository);
	}

	@Test
	@DisplayName("저장소가 없으면 계약 등록을 성공으로 처리하지 않는다")
	void rejectsWithoutStorage() {
		givenPropertyForUpdate(LeaseType.MONTHLY, PropertyStatus.ACTIVE);
		givenTenantChatRooms(seip);
		when(storageProvider.getIfAvailable()).thenReturn(null);

		assertContractError(ErrorCode.CONTRACT_STORAGE_UNAVAILABLE,
			request(2L, "9500000", "600000", 25, LocalDate.now()));
		verifyNoInteractions(imageProcessor, contractRepository);
	}

	@Test
	@DisplayName("이미지 하나라도 검사에 실패하면 계약과 계약서를 저장하지 않는다")
	void rejectsInvalidDocumentImage() {
		givenPropertyForUpdate(LeaseType.MONTHLY, PropertyStatus.ACTIVE);
		givenTenantChatRooms(seip);
		when(storageProvider.getIfAvailable()).thenReturn(storage);
		when(imageProcessor.process(any())).thenReturn(new byte[]{1})
			.thenThrow(ImageException.invalidImage());

		assertThrows(ImageException.class, () -> contractService.createContract(101L,
			request(2L, "9500000", "600000", 25, LocalDate.now()), List.of(photo(), photo())));
		verifyNoInteractions(contractRepository, storage);
	}

	@Test
	@DisplayName("저장 중 실패하면 저장 오류로 응답하고, 롤백되면 이번에 올린 파일을 지운다")
	void deletesUploadedDocumentsOnRollback() {
		givenPropertyForUpdate(LeaseType.MONTHLY, PropertyStatus.ACTIVE);
		givenTenantChatRooms(seip);
		givenSavedContract();
		when(storageProvider.getIfAvailable()).thenReturn(storage);
		when(imageProcessor.process(any())).thenReturn(new byte[]{1});
		doThrow(new RuntimeException("S3 down")).when(storage).write(anyString(), any());
		TransactionSynchronizationManager.initSynchronization();

		assertContractError(ErrorCode.CONTRACT_STORAGE_UNAVAILABLE,
			request(2L, "9500000", "600000", 25, LocalDate.now()));
		verify(contractDocumentRepository, never()).save(any());

		var key = ArgumentCaptor.forClass(String.class);
		verify(storage).write(key.capture(), any());
		TransactionSynchronizationManager.getSynchronizations()
			.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
		verify(storage).delete(key.getValue());
	}

	@Test
	@DisplayName("임대인은 매물의 임대인으로, 임차인 후보는 이 매물로 상담한 채팅방 문의자로 최근 순서대로 준다")
	void readsLandlordAndTenantCandidates() {
		givenAssignedProperty();
		givenTenantChatRooms(seip, tenant(3L, "김보금", "b@example.com", UserStatus.ACTIVE));

		var response = contractService.readContractCandidates(101L, 20L);

		assertEquals(20L, response.getPropertyId());
		assertEquals(LeaseType.MONTHLY, response.getLeaseType());
		assertEquals(new BigDecimal("650000"), response.getMonthlyRent());
		assertEquals("박임대", response.getLandlord().getName());
		assertEquals("pa****@example.com", response.getLandlord().getMaskedEmail());
		assertEquals(List.of(2L, 3L), response.getTenants().stream().map(ContractDto.PartyResponse::getUserId).toList());
		// 앞부분이 두 글자보다 짧으면 있는 글자만 남긴다
		assertEquals("b****@example.com", response.getTenants().get(1).getMaskedEmail());
	}

	@Test
	@DisplayName("탈퇴한 회원과 임대인 본인은 임차인 후보에서 뺀다")
	void excludesWithdrawnUsersAndLandlord() {
		givenAssignedProperty();
		givenTenantChatRooms(tenant(2L, "최세입", "seip@example.com", UserStatus.WITHDRAWN), landlord,
			tenant(3L, "김보금", "bogeum@example.com", UserStatus.ACTIVE));

		var response = contractService.readContractCandidates(101L, 20L);

		assertEquals(List.of(3L), response.getTenants().stream().map(ContractDto.PartyResponse::getUserId).toList());
	}

	@Test
	@DisplayName("상담한 사람이 없으면 임차인 후보를 빈 목록으로 준다")
	void returnsEmptyTenantsWithoutChatRooms() {
		givenAssignedProperty();
		givenTenantChatRooms();

		var response = contractService.readContractCandidates(101L, 20L);

		assertEquals(List.of(), response.getTenants());
	}

	@Test
	@DisplayName("없거나 다른 중개사가 맡은 매물은 조회할 수 없다")
	void rejectsUnassignedProperty() {
		when(propertyRepository.findByPropertyIdAndAgent_UserId(20L, 999L)).thenReturn(Optional.empty());

		var exception = assertThrows(PropertyException.class, () -> contractService.readContractCandidates(999L, 20L));

		assertEquals(ErrorCode.PROPERTY_NOT_FOUND, exception.getErrorCode());
		verifyNoInteractions(chatRepository);
	}

	@Test
	@DisplayName("중개 이력은 본인이 중개한 계약을 계약일·계약 번호 최신순으로 10개씩 주고 실제 계약 금액과 당사자 이름을 담는다")
	void readsBrokeredContracts() {
		Property property = Property.builder().propertyId(20L).roadAddress("서울 마포구 월드컵로 1").detailAddress("302호")
			.leaseType(LeaseType.MONTHLY).deposit(new BigDecimal("10000000")).build();
		Contract contract = Contract.builder().contractId(30L).property(property).landlord(landlord).tenant(seip)
			.agent(agent).leaseType(LeaseType.MONTHLY).deposit(new BigDecimal("9500000"))
			.monthlyRent(new BigDecimal("600000")).paymentDay(25).startAt(START.atStartOfDay()).endAt(END.atStartOfDay())
			.brokerageFee(new BigDecimal("300000")).contractedAt(LocalDate.of(2026, 11, 8).atStartOfDay()).build();
		var pageable = ArgumentCaptor.forClass(Pageable.class);
		when(contractRepository.findAllByAgent_UserId(eq(101L), pageable.capture()))
			.thenAnswer(invocation -> new PageImpl<>(List.of(contract), invocation.getArgument(1), 11));

		var response = contractService.readContracts(101L, 2);

		assertEquals(1, pageable.getValue().getPageNumber());
		assertEquals(10, pageable.getValue().getPageSize());
		assertEquals(Sort.by(Sort.Direction.DESC, "contractedAt", "contractId"), pageable.getValue().getSort());
		assertEquals(2, response.getPage());
		assertEquals(2, response.getTotalPages());
		assertEquals(11L, response.getTotalElements());
		var item = response.getContracts().get(0);
		assertEquals(30L, item.getContractId());
		assertEquals(20L, item.getPropertyId());
		assertEquals("서울 마포구 월드컵로 1", item.getRoadAddress());
		assertEquals(new BigDecimal("9500000"), item.getDeposit());
		assertEquals(new BigDecimal("600000"), item.getMonthlyRent());
		assertEquals(new BigDecimal("300000"), item.getBrokerageFee());
		assertEquals("박임대", item.getLandlordName());
		assertEquals("최세입", item.getTenantName());
		assertEquals(START, item.getStartAt());
		assertEquals(END, item.getEndAt());
		assertEquals(LocalDate.of(2026, 11, 8), item.getContractedAt());
	}

	@Test
	@DisplayName("중개 이력 페이지가 1보다 작으면 첫 페이지를 주고, 계약이 없으면 빈 목록을 준다")
	void readsFirstPageWhenPageBelowOne() {
		var pageable = ArgumentCaptor.forClass(Pageable.class);
		when(contractRepository.findAllByAgent_UserId(eq(101L), pageable.capture()))
			.thenAnswer(invocation -> Page.empty(invocation.getArgument(1)));

		var response = contractService.readContracts(101L, 0);

		assertEquals(0, pageable.getValue().getPageNumber());
		assertEquals(List.of(), response.getContracts());
		assertEquals(1, response.getPage());
		assertEquals(0L, response.getTotalElements());
	}

	private void assertContractError(ErrorCode errorCode, ContractDto.ContractRequest request) {
		var exception = assertThrows(ContractException.class,
			() -> contractService.createContract(101L, request, List.of(photo())));
		assertEquals(errorCode, exception.getErrorCode());
	}

	private Property givenPropertyForUpdate(LeaseType leaseType, PropertyStatus status) {
		Property property = Property.builder().propertyId(20L).landlord(landlord).agent(agent).leaseType(leaseType)
			.deposit(new BigDecimal("10000000")).propertyStatus(status).build();
		when(propertyRepository.findForUpdateByPropertyIdAndAgent_UserId(20L, 101L)).thenReturn(Optional.of(property));
		return property;
	}

	private void givenAssignedProperty() {
		Property property = Property.builder().propertyId(20L).landlord(landlord).leaseType(LeaseType.MONTHLY)
			.deposit(new BigDecimal("10000000")).monthlyRent(new BigDecimal("650000"))
			.propertyStatus(PropertyStatus.ACTIVE).build();
		when(propertyRepository.findByPropertyIdAndAgent_UserId(20L, 101L)).thenReturn(Optional.of(property));
	}

	private void givenTenantChatRooms(User... inquirers) {
		when(chatRepository.findAllByProperty_PropertyIdAndAgent_UserIdAndRoomTypeOrderByCreatedAtDesc(20L, 101L,
			ChatRoomType.TENANT_AGENT))
			.thenReturn(java.util.Arrays.stream(inquirers)
				.map(inquirer -> ChatRoom.builder().roomType(ChatRoomType.TENANT_AGENT).inquirer(inquirer).build())
				.toList());
	}

	// DB가 번호를 붙이는 것처럼 저장한 계약에 번호 30을 넣는다
	private void givenSavedContract() {
		when(contractRepository.save(any(Contract.class))).thenAnswer(invocation -> {
			Contract contract = invocation.getArgument(0);
			ReflectionTestUtils.setField(contract, "contractId", 30L);
			return contract;
		});
	}

	private static ContractDto.ContractRequest request(Long tenantId, String deposit, String monthlyRent,
			Integer paymentDay, LocalDate contractedAt) {
		return new ContractDto.ContractRequest(20L, tenantId, new BigDecimal(deposit),
			monthlyRent == null ? null : new BigDecimal(monthlyRent), paymentDay, START, END, contractedAt,
			new BigDecimal("300000"));
	}

	private static MultipartFile photo() {
		return new MockMultipartFile("images", "contract.jpg", "image/jpeg", new byte[]{1, 2, 3});
	}

	private static User tenant(Long userId, String name, String email, UserStatus userStatus) {
		return User.builder().userId(userId).name(name).email(email).userStatus(userStatus).build();
	}
}
