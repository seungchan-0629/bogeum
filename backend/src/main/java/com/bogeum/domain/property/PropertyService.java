package com.bogeum.domain.property;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.bogeum.common.enums.ApprovalStatus;
import com.bogeum.common.enums.BrokerageStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
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
	// 매물 목록을 한 번에 가져오는 개수. 카드가 2·3·4열 어느 쪽이어도 줄이 맞는다 (PROPERTY_004)
	private static final int LIST_SIZE = 12;
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
	 * 공개 중(ACTIVE)인 매물을 최근 공개 순서로 이어서 조회한다.
	 * 스크롤 목록이라 페이지 번호 대신 앞 목록의 마지막 매물 다음부터 보여준다.
	 * 저장소가 없으면 사진 주소만 비우고 목록 조회는 막지 않는다.
	 *
	 * @param cursorPublishedAt 앞 목록 마지막 매물의 공개 시각. 첫 목록이면 둘 다 비운다
	 * @param cursorPropertyId 앞 목록 마지막 매물의 번호
	 * @throws PropertyException 커서 값이 하나만 온 경우
	 */
	@Transactional(readOnly = true)
	public PropertyDto.PropertyListResponse readProperties(LocalDateTime cursorPublishedAt, Long cursorPropertyId) {
		if ((cursorPublishedAt == null) != (cursorPropertyId == null)) {
			throw PropertyException.invalidCursor();
		}
		Pageable pageable = PageRequest.of(0, LIST_SIZE);
		Slice<Property> properties = cursorPropertyId == null
			? propertyRepository.findPublished(PropertyStatus.ACTIVE, pageable)
			: propertyRepository.findPublishedAfter(PropertyStatus.ACTIVE, cursorPublishedAt, cursorPropertyId, pageable);

		Map<Long, String> thumbnailKeys = findThumbnailKeys(properties.getContent());
		PropertyImageStorage storage = thumbnailKeys.isEmpty() ? null : storageProvider.getIfAvailable();
		List<PropertyDto.PropertyItemResponse> items = properties.stream()
			.map(property -> {
				String key = thumbnailKeys.get(property.getPropertyId());
				return PropertyDto.PropertyItemResponse.from(property,
					key == null || storage == null ? null : storage.createReadUrl(key));
			})
			.toList();
		Property last = properties.hasNext() ? properties.getContent().get(properties.getNumberOfElements() - 1) : null;
		return PropertyDto.PropertyListResponse.builder()
			.properties(items)
			.hasNext(properties.hasNext())
			.nextCursor(last == null ? null : PropertyDto.PropertyCursor.builder()
				.cursorPublishedAt(last.getPublishedAt())
				.cursorPropertyId(last.getPropertyId())
				.build())
			.build();
	}

	/**
	 * 공개 중(ACTIVE)인 매물의 상세 정보, 사진, 담당 중개사를 조회한다 (PROPERTY_003).
	 * 사용자는 담당 중개사에게 채팅으로 문의한다. 저장소가 없으면 사진 주소만 비우고 조회는 막지 않는다.
	 *
	 * @throws PropertyException 없거나 공개 중이 아닌 매물(공개 전·숨김·계약 완료·삭제)인 경우
	 */
	@Transactional(readOnly = true)
	public PropertyDto.PublicDetailResponse readPublicProperty(Long propertyId) {
		Property property = propertyRepository.findByPropertyIdAndPropertyStatus(propertyId, PropertyStatus.ACTIVE)
			.orElseThrow(PropertyException::propertyNotFound);
		List<PropertyImage> images = propertyImageRepository
			.findAllByProperty_PropertyIdOrderBySortOrderAscPropertyImageIdAsc(propertyId);
		PropertyImageStorage storage = images.isEmpty() ? null : storageProvider.getIfAvailable();
		List<PropertyDto.PublicImageResponse> imageResponses = images.stream()
			.map(image -> PropertyDto.PublicImageResponse.from(image,
				storage == null ? null : storage.createReadUrl(image.getFileKey())))
			.toList();
		// 공개된 매물은 중개사가 수락해 올린 매물이라 담당 중개사가 있다. 사무소 정보가 없어도 이름은 보여 준다
		User agentUser = property.getAgent();
		Agent agent = agentRepository.findByUser_UserId(agentUser.getUserId()).orElse(null);
		return PropertyDto.PublicDetailResponse.from(property, imageResponses,
			PropertyDto.AgentInfoResponse.from(agentUser, agent));
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

	/**
	 * 중개사가 맡은 매물을 공개(ACTIVE)한다. 필수 상세 정보와 사진 1장 이상이 있어야 한다 (PROPERTY_010).
	 *
	 * @throws PropertyException 없거나 맡지 않은 매물, 공개 전이 아닌 매물, 필수 정보나 사진이 없는 경우
	 */
	@Transactional
	public PropertyDto.DetailSaveResponse publishProperty(Long agentUserId, Long propertyId) {
		// 사진 삭제와 겹쳐 사진 없는 매물이 공개되지 않도록 매물 행을 잠근다
		Property property = propertyRepository.findForUpdateByPropertyIdAndAgent_UserId(propertyId, agentUserId)
			.orElseThrow(PropertyException::propertyNotFound);
		property.publish(propertyImageRepository.existsByProperty_PropertyId(propertyId));
		return PropertyDto.DetailSaveResponse.from(property);
	}

	/**
	 * 중개사가 맡은 공개 전 매물의 사진 하나를 지운다 (PROPERTY_010).
	 * DB에서 먼저 지우고, 커밋된 뒤에 저장소 파일을 지운다.
	 *
	 * @throws PropertyException 없거나 맡지 않은 매물, 공개 전이 아닌 매물, 그 매물의 사진이 아닌 경우, 저장소 없음
	 */
	@Transactional
	public void deletePropertyImage(Long agentUserId, Long propertyId, Long propertyImageId) {
		// 공개하기의 사진 수 확인과 겹치지 않도록 사진 추가처럼 매물 행을 잠근다
		Property property = propertyRepository.findForUpdateByPropertyIdAndAgent_UserId(propertyId, agentUserId)
			.orElseThrow(PropertyException::propertyNotFound);
		property.validateEditable();
		PropertyImage image = propertyImageRepository.findByPropertyImageIdAndProperty_PropertyId(propertyImageId, propertyId)
			.orElseThrow(PropertyException::propertyImageNotFound);
		PropertyImageStorage storage = storageProvider.getIfAvailable();
		// 저장소 없이 지우면 파일이 남은 채 행만 사라지므로 성공으로 처리하지 않는다
		if (storage == null) {
			throw PropertyException.storageUnavailable();
		}
		propertyImageRepository.delete(image);

		String key = image.getFileKey();
		// 롤백되면 사진이 그대로 보여야 하므로 커밋된 뒤에만 파일을 지운다
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				try {
					storage.delete(key);
				} catch (RuntimeException exception) {
					log.error("삭제한 매물 사진 정리 실패: key={}", key, exception);
				}
			}
		});
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

	// 매물별 대표 사진 키. 사진이 순서대로 오니 매물마다 처음 나온 사진이 대표 사진이다
	private Map<Long, String> findThumbnailKeys(List<Property> properties) {
		if (properties.isEmpty()) {
			return Map.of();
		}
		Map<Long, String> keys = new HashMap<>();
		List<Long> propertyIds = properties.stream().map(Property::getPropertyId).toList();
		for (PropertyImage image : propertyImageRepository
				.findAllByProperty_PropertyIdInOrderBySortOrderAscPropertyImageIdAsc(propertyIds)) {
			keys.putIfAbsent(image.getProperty().getPropertyId(), image.getFileKey());
		}
		return keys;
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
