package com.bogeum.domain.aftersales;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.bogeum.domain.aftersales.model.AfterSalesDto;
import com.bogeum.domain.aftersales.model.AfterSalesRequest;
import com.bogeum.domain.aftersales.model.AfterSalesRequestImage;
import com.bogeum.domain.contract.ContractRepository;
import lombok.extern.slf4j.Slf4j;

/** 계약 임차인·기간을 검증하고 AS 접수와 사진 저장 키를 같은 DB 트랜잭션으로 기록한다 (AS_001). */
@Service
@Slf4j
public class AfterSalesService {
	private final AfterSalesRepository afterSalesRepository;
	private final ContractRepository contractRepository;
	private final AfterSalesRequestImageRepository imageRepository;
	private final AfterSalesImageProcessor imageProcessor;
	private final ObjectProvider<AfterSalesImageStorage> storageProvider;
	private final int maxCount;

	public AfterSalesService(AfterSalesRepository afterSalesRepository, ContractRepository contractRepository,
			AfterSalesRequestImageRepository imageRepository, AfterSalesImageProcessor imageProcessor,
			ObjectProvider<AfterSalesImageStorage> storageProvider, @Value("${as-image.max-count:10}") int maxCount) {
		this.afterSalesRepository = afterSalesRepository;
		this.contractRepository = contractRepository;
		this.imageRepository = imageRepository;
		this.imageProcessor = imageProcessor;
		this.storageProvider = storageProvider;
		if (maxCount <= 0) {
			throw new IllegalArgumentException("사진 개수 상한은 1 이상이어야 합니다.");
		}
		this.maxCount = maxCount;
	}

	/**
	 * 계약에서 확인한 매물·임차인으로 접수하고 처음 첨부한 사진만 처리한다.
	 * 사진이 없으면 저장소 없이 접수할 수 있지만, 사진이 있으면 저장 어댑터가 필요하다.
	 *
	 * @throws AfterSalesException 계약 없음·임차인 불일치·기간 초과·사진 검증 또는 저장 실패 시
	 */
	@Transactional
	public AfterSalesDto.CreateResponse createAfterSalesRequest(Long userId, AfterSalesDto.CreateRequest request,
			List<MultipartFile> images) {
		var contract = contractRepository.findById(request.getContractId())
			.orElseThrow(AfterSalesException::contractNotFound);
		// USER 역할만으로 임차인을 판정하지 않고 해당 계약의 당사자인지 확인한다.
		if (!contract.getTenant().getUserId().equals(userId)) {
			throw AfterSalesException.forbidden();
		}
		// 한국 날짜 기준으로 시작일·종료일을 포함하며 계약 시각의 시·분은 비교하지 않는다.
		LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
		if (today.isBefore(contract.getStartAt().toLocalDate()) || today.isAfter(contract.getEndAt().toLocalDate())) {
			throw AfterSalesException.outsideContract();
		}
		List<MultipartFile> files = images == null ? List.of() : images;
		if (files.size() > maxCount) {
			throw AfterSalesException.invalidImage();
		}
		var storage = files.isEmpty() ? null : storageProvider.getIfAvailable();
		// 실제 저장소 미연결 상태에서 사진 첨부를 성공으로 처리하거나 접수만 남기지 않는다.
		if (!files.isEmpty() && storage == null) {
			throw AfterSalesException.storageUnavailable();
		}
		var saved = afterSalesRepository.save(AfterSalesRequest.builder().property(contract.getProperty())
			.tenant(contract.getTenant()).content(request.getContent().trim()).build());
		List<String> keys = new ArrayList<>();
		if (storage != null) {
			// DB 커밋 실패까지 정리하되 외부 저장소와 DB의 원자성을 보장하지는 않는다.
			// 런타임 예외로 롤백되거나 커밋되지 않으면 이번 요청에서 시도한 키를 정리한다.
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					if (status != STATUS_COMMITTED) {
						deleteImages(storage, keys);
					}
				}
			});
			for (MultipartFile file : files) {
				byte[] jpeg = imageProcessor.process(file);
				String key = "as/" + userId + "/" + UUID.randomUUID() + ".jpg";
				// 저장 도중 일부만 기록된 경우도 정리할 수 있도록 쓰기 전에 키를 등록한다.
				keys.add(key);
				try {
					storage.write(key, jpeg);
				} catch (RuntimeException exception) {
					log.error("AS 사진 저장 실패: key={}", key, exception);
					throw AfterSalesException.storageUnavailable();
				}
				imageRepository.save(AfterSalesRequestImage.builder().asRequest(saved).fileKey(key).build());
			}
		}
		return AfterSalesDto.CreateResponse.from(saved);
	}

	/** 정리 실패는 로그로 남기고 다음 키를 계속 정리한다. 롤백 자체를 되돌리지는 않는다. */
	private void deleteImages(AfterSalesImageStorage storage, List<String> keys) {
		for (String key : keys) {
			try {
				storage.delete(key);
			} catch (RuntimeException exception) {
				log.error("롤백된 AS 사진 정리 실패: key={}", key, exception);
			}
		}
	}
}
