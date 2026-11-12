package com.bogeum.domain.contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.UserStatus;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContractService {

	// 계약 하나에 올릴 수 있는 계약서 이미지 수. 매물 사진 상한과 같다 (PROPERTY_011)
	private static final int MAX_DOCUMENT_COUNT = 10;
	// 중개 이력 한 페이지의 계약 수 (AGENT_003)
	private static final int PAGE_SIZE = 10;

	private final PropertyRepository propertyRepository;
	private final ChatRepository chatRepository;
	private final ContractRepository contractRepository;
	private final ContractDocumentRepository contractDocumentRepository;
	private final ObjectProvider<ContractDocumentStorage> storageProvider;
	private final ImageProcessor imageProcessor;

	/**
	 * 중개사가 맡은 공개 매물의 계약 정보와 계약서 이미지를 등록하고 매물을 계약 완료로 바꾼다 (PROPERTY_011).
	 * 모든 이미지를 먼저 검사·변환해서, 하나라도 잘못되면 저장소에 아무것도 올리지 않는다.
	 *
	 * @throws PropertyException 없거나 맡지 않은 매물, 공개 중이 아닌 매물인 경우
	 * @throws ContractException 후보가 아닌 임차인, 거래 유형에 맞지 않는 금액, 잘못된 기간·계약일,
	 *                           계약서가 없거나 상한을 넘는 경우, 저장 실패
	 * @throws com.bogeum.common.exception.ImageException 형식·용량·픽셀 제한을 벗어난 이미지가 있는 경우
	 */
	@Transactional
	public ContractDto.SaveResponse createContract(Long agentUserId, ContractDto.ContractRequest request,
			List<MultipartFile> images) {
		// 같은 매물을 동시에 두 번 계약하지 않도록 매물 행을 잠근다
		Property property = propertyRepository.findForUpdateByPropertyIdAndAgent_UserId(request.getPropertyId(),
			agentUserId).orElseThrow(PropertyException::propertyNotFound);
		property.contract();
		BigDecimal monthlyRent = validateContractPrice(property.getLeaseType(), request);
		validateContractDates(request);
		User tenant = findTenantCandidates(property, agentUserId).stream()
			.filter(user -> user.getUserId().equals(request.getTenantId()))
			.findFirst()
			.orElseThrow(ContractException::invalidTenant);
		List<MultipartFile> files = images == null ? List.of() : images;
		if (files.isEmpty() || files.size() > MAX_DOCUMENT_COUNT) {
			throw ContractException.invalidImage();
		}
		ContractDocumentStorage storage = storageProvider.getIfAvailable();
		// 저장소 없이 계약서 등록을 성공으로 처리하지 않는다
		if (storage == null) {
			throw ContractException.storageUnavailable();
		}
		List<byte[]> jpegs = files.stream().map(imageProcessor::process).toList();

		Contract contract = contractRepository.save(Contract.builder()
			.property(property)
			.landlord(property.getLandlord())
			.tenant(tenant)
			.agent(property.getAgent())
			.leaseType(property.getLeaseType())
			.deposit(request.getDeposit())
			.monthlyRent(monthlyRent)
			.paymentDay(request.getPaymentDay())
			.startAt(request.getStartAt().atStartOfDay())
			.endAt(request.getEndAt().atStartOfDay())
			.brokerageFee(request.getBrokerageFee())
			.contractedAt(request.getContractedAt().atStartOfDay())
			.build());
		saveDocuments(storage, contract, jpegs);
		return ContractDto.SaveResponse.from(contract);
	}

	/**
	 * 중개사 본인이 중개해 계약 정보를 등록한 계약을 페이지 단위로 조회한다 (AGENT_003).
	 * 계약일은 하루 단위라 같은 날 계약이 많아서, 페이지를 넘겨도 순서가 섞이지 않게 계약 번호로 한 번 더 정렬한다.
	 *
	 * @param page 1부터 센다. 1보다 작으면 첫 페이지를 준다
	 */
	@Transactional(readOnly = true)
	public ContractDto.ContractListResponse readContracts(Long agentUserId, int page) {
		Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE,
			Sort.by(Sort.Direction.DESC, "contractedAt", "contractId"));
		return ContractDto.ContractListResponse.from(contractRepository.findAllByAgent_UserId(agentUserId, pageable));
	}

	/**
	 * 중개사가 맡은 매물의 계약 등록 화면 첫 값을 조회한다 (PROPERTY_011).
	 * 임대인은 중개를 신청한 매물의 임대인이고, 임차인 후보는 이 매물로 본인과 상담한 채팅방의 문의자다.
	 *
	 * @throws PropertyException 없거나 본인이 맡은 매물이 아닌 경우
	 */
	@Transactional(readOnly = true)
	public ContractDto.CandidateResponse readContractCandidates(Long agentUserId, Long propertyId) {
		Property property = propertyRepository.findByPropertyIdAndAgent_UserId(propertyId, agentUserId)
			.orElseThrow(PropertyException::propertyNotFound);
		return ContractDto.CandidateResponse.from(property, findTenantCandidates(property, agentUserId));
	}

	// 탈퇴한 회원과는 계약할 수 없고, 임대인이 같은 매물의 임차인이 될 수는 없다
	private List<User> findTenantCandidates(Property property, Long agentUserId) {
		Long landlordUserId = property.getLandlord().getUserId();
		return chatRepository
			.findAllByProperty_PropertyIdAndAgent_UserIdAndRoomTypeOrderByCreatedAtDesc(property.getPropertyId(),
				agentUserId, ChatRoomType.TENANT_AGENT)
			.stream()
			.map(ChatRoom::getInquirer)
			.filter(user -> user.getUserStatus() == UserStatus.ACTIVE && !user.getUserId().equals(landlordUserId))
			.toList();
	}

	// 거래 유형은 매물에서 정해져 있다. 월세는 자동 결제(RENT_004)에 쓰여 월세와 납부일이 함께 있어야 한다
	private BigDecimal validateContractPrice(LeaseType leaseType, ContractDto.ContractRequest request) {
		BigDecimal rent = request.getMonthlyRent() == null ? BigDecimal.ZERO : request.getMonthlyRent();
		boolean isValid = leaseType == LeaseType.JEONSE
			? rent.signum() == 0 && request.getDeposit().signum() > 0 && request.getPaymentDay() == null
			: rent.signum() > 0 && request.getPaymentDay() != null;
		if (!isValid) {
			throw ContractException.invalidPrice();
		}
		return rent;
	}

	// 아직 쓰지 않은 계약은 등록할 수 없다. 계약일이 오늘 이전이면 계약서를 며칠 뒤에 올려도 된다
	private void validateContractDates(ContractDto.ContractRequest request) {
		if (!request.getStartAt().isBefore(request.getEndAt())) {
			throw ContractException.invalidPeriod();
		}
		if (request.getContractedAt().isAfter(LocalDate.now()) || !request.getContractedAt().isBefore(request.getEndAt())) {
			throw ContractException.invalidContractedAt();
		}
	}

	private void saveDocuments(ContractDocumentStorage storage, Contract contract, List<byte[]> jpegs) {
		List<String> keys = new ArrayList<>();
		// 롤백되거나 커밋되지 않으면 이번 요청에서 올린 파일을 지운다. 외부 저장소와 DB의 원자성까지 보장하지는 않는다
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					deleteDocuments(storage, keys);
				}
			}
		});
		for (byte[] jpeg : jpegs) {
			String key = "contract-documents/" + contract.getContractId() + "/" + UUID.randomUUID() + ".jpg";
			// 저장 도중 일부만 기록된 경우도 지울 수 있도록 쓰기 전에 키를 등록한다
			keys.add(key);
			try {
				storage.write(key, jpeg);
			} catch (RuntimeException exception) {
				log.error("계약서 저장 실패: key={}", key, exception);
				throw ContractException.storageUnavailable();
			}
			contractDocumentRepository.save(ContractDocument.builder()
				.contract(contract).uploadedBy(contract.getAgent()).fileKey(key).build());
		}
	}

	// 정리 실패가 원래 실패 원인을 가리지 않도록 로그만 남긴다
	private void deleteDocuments(ContractDocumentStorage storage, List<String> keys) {
		for (String key : keys) {
			try {
				storage.delete(key);
			} catch (RuntimeException exception) {
				log.error("계약서 정리 실패: key={}", key, exception);
			}
		}
	}
}
