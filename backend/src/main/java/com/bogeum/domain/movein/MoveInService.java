package com.bogeum.domain.movein;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.bogeum.domain.aftersales.AfterSalesImageStorage;
import com.bogeum.domain.contract.ContractRepository;
import com.bogeum.domain.movein.model.MoveInDefectPhoto;
import com.bogeum.utils.ImageProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 계약 당사자 권한을 확인해 비공개 입주 사진을 등록·조회한다. */
@Service
@Slf4j
@RequiredArgsConstructor
public class MoveInService {
	private static final int MAX_PHOTOS_PER_REQUEST = 10;

	private final MoveInRepository moveInRepository;
	private final ContractRepository contractRepository;
	private final ImageProcessor imageProcessor;
	// 기존 비공개 저장소를 접두사로 구분해 사용하며 AS의 접수·댓글 로직에는 의존하지 않는다.
	private final ObjectProvider<AfterSalesImageStorage> storageProvider;

	/** 누적 상한이나 교체 없이 새 사진을 추가하며 실패하면 이번 요청 전체를 롤백한다. */
	@Transactional
	public List<MoveInDefectPhoto> uploadPhotos(Long userId, Long contractId, List<MultipartFile> images,
			List<String> captions) {
		if (contractId == null || contractId <= 0) {
			throw MoveInException.invalidRequest();
		}
		var contract = contractRepository.findById(contractId).orElseThrow(MoveInException::contractNotFound);
		if (!contract.getTenant().getUserId().equals(userId)) {
			throw MoveInException.forbidden();
		}
		validateUploadDate(contract.getContractedAt().toLocalDate(), LocalDate.now(ZoneId.of("Asia/Seoul")));
		if (images == null || images.isEmpty() || images.size() > MAX_PHOTOS_PER_REQUEST) {
			throw MoveInException.invalidImage();
		}
		if (captions == null || captions.size() != images.size()
				|| captions.stream().anyMatch(caption -> caption == null || caption.length() > MoveInDefectPhoto.MAX_CAPTION_LENGTH)) {
			throw MoveInException.invalidRequest();
		}
		var storage = storageProvider.getIfAvailable();
		if (storage == null) {
			throw MoveInException.storageUnavailable();
		}
		// 하나라도 잘못된 사진이면 외부 저장소에 쓰기 전에 전체 요청을 거절한다.
		List<byte[]> jpegs = images.stream().map(imageProcessor::process).toList();
		List<String> keys = new ArrayList<>();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					deletePhotos(storage, keys);
				}
			}
		});
		List<MoveInDefectPhoto> photos = new ArrayList<>();
		for (int index = 0; index < jpegs.size(); index++) {
			String key = "movein-defect-photos/" + contractId + "/" + UUID.randomUUID() + ".jpg";
			// 저장 후 응답이 실패한 경우도 정리하도록 시도 전에 키를 기록한다.
			keys.add(key);
			try {
				storage.write(key, jpegs.get(index));
			} catch (RuntimeException exception) {
				log.error("입주 사진 저장 실패: key={}", key, exception);
				throw MoveInException.storageUnavailable();
			}
			photos.add(MoveInDefectPhoto.builder().contract(contract).uploadedBy(contract.getTenant())
				.fileKey(key).caption(captions.get(index)).build());
		}
		return moveInRepository.saveAllAndFlush(photos);
	}

	/** 계약 임대인에게만 사진과 임시 조회 주소를 제공하며 업로드 기한은 검사하지 않는다 (MOVEIN_002). */
	@Transactional(readOnly = true)
	public List<PhotoReadResult> readPhotos(Long userId, Long contractId) {
		if (contractId == null || contractId <= 0) {
			throw MoveInException.invalidRequest();
		}
		var contract = contractRepository.findById(contractId).orElseThrow(MoveInException::contractNotFound);
		if (!contract.getLandlord().getUserId().equals(userId)) {
			throw MoveInException.forbidden();
		}
		var photos = moveInRepository.findAllByContract_ContractIdOrderByCreatedAtAscMoveInDefectPhotoIdAsc(contractId);
		// 사진이 없으면 저장소에 접근하지 않는다. 미연결 상태는 AS처럼 메타데이터만 반환한다.
		var storage = photos.isEmpty() ? null : storageProvider.getIfAvailable();
		return photos.stream().map(photo -> {
			String url = null;
			if (storage != null) {
				try {
					url = storage.createReadUrl(photo.getFileKey());
				} catch (RuntimeException exception) {
					log.error("입주 사진 조회 주소 발급 실패: photoId={}", photo.getMoveInDefectPhotoId(), exception);
					throw MoveInException.readUrlUnavailable();
				}
			}
			return new PhotoReadResult(photo, url);
		}).toList();
	}

	/** HTTP 응답 DTO에 의존하지 않고 권한 확인된 사진과 주소를 Controller에 전달한다. */
	public record PhotoReadResult(MoveInDefectPhoto photo, String url) {}

	// contractedAt은 날짜로 등록된다. 한국 날짜 기준 계약 당일과 +7일의 날짜 전체를 포함한다.
	static void validateUploadDate(LocalDate contractedDate, LocalDate today) {
		if (today.isBefore(contractedDate) || today.isAfter(contractedDate.plusDays(7))) {
			throw MoveInException.outsideUploadPeriod();
		}
	}

	private void deletePhotos(AfterSalesImageStorage storage, List<String> keys) {
		for (String key : keys) {
			try {
				storage.delete(key);
			} catch (RuntimeException exception) {
				// 외부 저장소와 DB의 원자성은 보장하지 않는다. 정리 실패가 원래 오류를 가리지 않게 한다.
				log.error("입주 사진 정리 실패: key={}", key, exception);
			}
		}
	}
}
