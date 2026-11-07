package com.bogeum.domain.property;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.bogeum.common.enums.ApprovalStatus;
import com.bogeum.common.enums.BrokerageStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.domain.agent.AgentRepository;
import com.bogeum.domain.agent.model.Agent;
import com.bogeum.domain.property.model.BrokerageRequest;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.property.model.PropertyDto;
import com.bogeum.domain.property.model.PropertyImage;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PropertyService {

	// 중개 신청 목록 한 페이지의 개수 (PROPERTY_012)
	private static final int PAGE_SIZE = 5;
	// 매물 하나에 올릴 수 있는 사진 수. AS 사진 상한과 같다 (PROPERTY_010)
	private static final int MAX_IMAGE_COUNT = 10;

	private final UserService userService;
	private final AgentRepository agentRepository;
	private final PropertyRepository propertyRepository;
	private final BrokerageRequestRepository brokerageRequestRepository;
	private final PropertyImageRepository propertyImageRepository;
	private final ObjectProvider<PropertyImageStorage> storageProvider;
	private final ImageProcessor imageProcessor;

	/**
	 * 매물을 만들고 중개사에게 중개를 신청한다 (PROPERTY_002).
	 * 매물의 중개사는 수락 전까지 비워 두고, 신청 건에만 배정된 중개사를 기록한다 (PROPERTY_008).
	 *
	 * @throws PropertyException 임대 형식과 가격이 맞지 않거나 신청할 중개사가 없는 경우
	 */
	@Transactional
	public PropertyDto.BrokerageResponse createBrokerageRequest(Long landlordUserId, PropertyDto.BrokerageRequest request) {
		User landlord = userService.findUser(landlordUserId);
		BigDecimal monthlyRent = validateLeasePrice(request.getLeaseType(), request.getDeposit(), request.getMonthlyRent());
		boolean isDesignated = request.getAgentId() != null;
		Agent agent = isDesignated ? findDesignatedAgent(request.getAgentId()) : pickAgent(request.getRoadAddress());

		Property property = propertyRepository.save(Property.builder()
			.landlord(landlord)
			.leaseType(request.getLeaseType())
			.deposit(request.getDeposit())
			.monthlyRent(monthlyRent)
			.roadAddress(request.getRoadAddress().trim())
			.detailAddress(request.getDetailAddress())
			.build());
		BrokerageRequest brokerageRequest = brokerageRequestRepository.save(BrokerageRequest.builder()
			.property(property)
			.landlord(landlord)
			.agent(agent.getUser())
			.isDesignated(isDesignated)
			.build());
		return PropertyDto.BrokerageResponse.from(brokerageRequest, agent);
	}

	/**
	 * 중개사가 맡은 공개 전 매물에 사진을 추가한다. 기존 사진 뒤에 순서대로 붙는다 (PROPERTY_010).
	 * 모든 사진을 먼저 검사·변환해서, 하나라도 잘못되면 저장소에 아무것도 올리지 않는다.
	 *
	 * @throws PropertyException 없거나 맡지 않은 매물, 공개 전이 아닌 매물, 사진이 없거나 상한을 넘는 경우, 저장 실패
	 * @throws com.bogeum.common.exception.ImageException 형식·용량·픽셀 제한을 벗어난 사진이 있는 경우
	 */
	@Transactional
	public List<PropertyDto.ImageResponse> createPropertyImages(Long agentUserId, Long propertyId,
			List<MultipartFile> images) {
		Property property = propertyRepository.findForUpdateByPropertyIdAndAgent_UserId(propertyId, agentUserId)
			.orElseThrow(PropertyException::propertyNotFound);
		property.validateEditable();
		List<MultipartFile> files = images == null ? List.of() : images;
		List<PropertyImage> savedImages = propertyImageRepository
			.findAllByProperty_PropertyIdOrderBySortOrderAscPropertyImageIdAsc(propertyId);
		if (files.isEmpty() || savedImages.size() + files.size() > MAX_IMAGE_COUNT) {
			throw PropertyException.invalidImage();
		}
		PropertyImageStorage storage = storageProvider.getIfAvailable();
		// 저장소 없이 사진 추가를 성공으로 처리하지 않는다
		if (storage == null) {
			throw PropertyException.storageUnavailable();
		}
		List<byte[]> jpegs = files.stream().map(imageProcessor::process).toList();

		List<String> keys = new ArrayList<>();
		// 롤백되거나 커밋되지 않으면 이번 요청에서 올린 파일을 지운다. 외부 저장소와 DB의 원자성까지 보장하지는 않는다
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					deleteImages(storage, keys);
				}
			}
		});
		int sortOrder = savedImages.isEmpty() ? 0 : savedImages.get(savedImages.size() - 1).getSortOrder() + 1;
		List<PropertyDto.ImageResponse> responses = new ArrayList<>();
		for (byte[] jpeg : jpegs) {
			String key = "property-images/" + propertyId + "/" + UUID.randomUUID() + ".jpg";
			// 저장 도중 일부만 기록된 경우도 지울 수 있도록 쓰기 전에 키를 등록한다
			keys.add(key);
			try {
				storage.write(key, jpeg);
			} catch (RuntimeException exception) {
				log.error("매물 사진 저장 실패: key={}", key, exception);
				throw PropertyException.storageUnavailable();
			}
			PropertyImage image = propertyImageRepository.save(PropertyImage.builder()
				.property(property).fileKey(key).sortOrder(sortOrder++).build());
			responses.add(PropertyDto.ImageResponse.from(image, storage.createReadUrl(key)));
		}
		return responses;
	}

	/**
	 * 중개사 본인에게 온 중개 신청을 상태별로 페이지 단위로 조회한다 (PROPERTY_012).
	 * 새 신청은 신청한 순서, 수락한 신청은 수락한 순서로 최신이 위에 온다.
	 *
	 * @param page 1부터 센다. 1보다 작으면 첫 페이지를 준다
	 */
	@Transactional(readOnly = true)
	public PropertyDto.BrokerageListResponse readBrokerageRequests(Long agentUserId, BrokerageStatus status, int page) {
		String sortProperty = status == BrokerageStatus.ACCEPTED ? "acceptedAt" : "createdAt";
		Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, sortProperty));
		return PropertyDto.BrokerageListResponse.from(
			brokerageRequestRepository.findAllByAgent_UserIdAndBrokerageStatus(agentUserId, status, pageable));
	}

	/**
	 * 중개사가 맡은 매물의 신청 조건·상세 정보·사진을 조회한다 (PROPERTY_010).
	 * 저장소가 없으면 사진 정보만 주고 조회 자체는 막지 않는다.
	 *
	 * @throws PropertyException 없거나 본인이 맡은 매물이 아닌 경우
	 */
	@Transactional(readOnly = true)
	public PropertyDto.DetailResponse readPropertyDetail(Long agentUserId, Long propertyId) {
		Property property = propertyRepository.findByPropertyIdAndAgent_UserId(propertyId, agentUserId)
			.orElseThrow(PropertyException::propertyNotFound);
		List<PropertyImage> images = propertyImageRepository
			.findAllByProperty_PropertyIdOrderBySortOrderAscPropertyImageIdAsc(propertyId);
		PropertyImageStorage storage = images.isEmpty() ? null : storageProvider.getIfAvailable();
		List<PropertyDto.ImageResponse> imageResponses = images.stream()
			.map(image -> PropertyDto.ImageResponse.from(image,
				storage == null ? null : storage.createReadUrl(image.getFileKey())))
			.toList();
		return PropertyDto.DetailResponse.from(property, imageResponses);
	}

	/**
	 * 중개사가 본인에게 온 중개 신청을 수락하고 매물을 맡는다 (PROPERTY_008).
	 *
	 * @throws PropertyException 본인에게 온 신청이 아니거나 이미 수락한 경우
	 */
	@Transactional
	public PropertyDto.AcceptResponse acceptBrokerageRequest(Long agentUserId, Long brokerageRequestId) {
		BrokerageRequest brokerageRequest = brokerageRequestRepository
			.findByBrokerageRequestIdAndAgent_UserId(brokerageRequestId, agentUserId)
			.orElseThrow(PropertyException::brokerageRequestNotFound);
		brokerageRequest.accept();
		return PropertyDto.AcceptResponse.from(brokerageRequest);
	}

	/**
	 * 중개사가 맡은 매물의 상세 정보를 임시 저장한다. 공개(ACTIVE) 전환은 따로 한다 (PROPERTY_010).
	 *
	 * @throws PropertyException 없거나 맡지 않은 매물, 공개 전이 아닌 매물, 층이 전체 층보다 큰 경우
	 */
	@Transactional
	public PropertyDto.DetailSaveResponse updatePropertyDetail(Long agentUserId, Long propertyId,
			PropertyDto.DetailRequest request) {
		Property property = propertyRepository.findByPropertyIdAndAgent_UserId(propertyId, agentUserId)
			.orElseThrow(PropertyException::propertyNotFound);
		property.updateDetails(request.getPropertyType(), request.getAreaPyeong(), request.getRoomCount(),
			request.getFloor(), request.getTotalFloor(), request.getHouseholdCount(), request.getParkingCount(),
			trimToNull(request.getNearbySubwayStation()));
		return PropertyDto.DetailSaveResponse.from(property);
	}

	/** 전세는 월세 없이 전세금만, 월세는 월세 금액이 있어야 한다. 저장할 월세 금액을 반환한다. */
	private BigDecimal validateLeasePrice(LeaseType leaseType, BigDecimal deposit, BigDecimal monthlyRent) {
		BigDecimal rent = monthlyRent == null ? BigDecimal.ZERO : monthlyRent;
		boolean isValid = leaseType == LeaseType.JEONSE
			? rent.signum() == 0 && deposit.signum() > 0
			: rent.signum() > 0;
		if (!isValid) {
			throw PropertyException.invalidLeasePrice();
		}
		return rent;
	}

	private Agent findDesignatedAgent(Long agentId) {
		return agentRepository.findByAgentProfileIdAndApprovalStatus(agentId, ApprovalStatus.APPROVED)
			.orElseThrow(PropertyException::agentNotAvailable);
	}

	/** 희망 중개사가 없으면 같은 지역 중개사 중에서, 없으면 승인된 중개사 전체에서 무작위로 고른다. */
	private Agent pickAgent(String roadAddress) {
		List<Agent> approvedAgents = agentRepository.findAllByApprovalStatus(ApprovalStatus.APPROVED);
		if (approvedAgents.isEmpty()) {
			throw PropertyException.noAgentAvailable();
		}
		List<Agent> localAgents = approvedAgents.stream()
			.filter(agent -> isSameRegion(agent.getOfficeAddress(), roadAddress))
			.toList();
		List<Agent> candidates = localAgents.isEmpty() ? approvedAgents : localAgents;
		return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
	}

	/**
	 * 시·도와 시·군·구가 같으면 같은 지역으로 본다.
	 * 주소 API마다 "서울특별시"·"서울"처럼 시·도 표기가 달라 시·도는 앞 두 글자만 비교한다.
	 */
	private boolean isSameRegion(String officeAddress, String roadAddress) {
		String[] office = officeAddress.trim().split("\\s+");
		String[] property = roadAddress.trim().split("\\s+");
		if (office.length < 2 || property.length < 2) {
			return false;
		}
		return regionPrefix(office[0]).equals(regionPrefix(property[0])) && office[1].equals(property[1]);
	}

	private String regionPrefix(String sido) {
		return sido.length() <= 2 ? sido : sido.substring(0, 2);
	}

	/** 정리 실패는 로그로 남기고 다음 키를 계속 지운다. */
	private void deleteImages(PropertyImageStorage storage, List<String> keys) {
		for (String key : keys) {
			try {
				storage.delete(key);
			} catch (RuntimeException exception) {
				log.error("롤백된 매물 사진 정리 실패: key={}", key, exception);
			}
		}
	}

	// 공백만 보낸 칸은 비운 것으로 저장한다
	private String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
