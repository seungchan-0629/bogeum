package com.bogeum.domain.movein;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.bogeum.common.exception.ErrorCode;
import com.bogeum.common.exception.ImageException;
import com.bogeum.domain.aftersales.AfterSalesImageStorage;
import com.bogeum.domain.contract.ContractRepository;
import com.bogeum.domain.contract.model.Contract;
import com.bogeum.domain.movein.model.MoveInDefectPhoto;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

/** 기간 경계·요청 대응·실패 보상 로직을 DB·S3 없이 확인한다. */
@ExtendWith(MockitoExtension.class)
class MoveInServiceTest {
	@Mock MoveInRepository photos;
	@Mock ContractRepository contracts;
	@Mock ImageProcessor processor;
	@Mock ObjectProvider<AfterSalesImageStorage> provider;
	@Mock AfterSalesImageStorage storage;
	MoveInService service;
	final MockMultipartFile file = new MockMultipartFile("images", new byte[]{1});
	final User tenant = User.builder().userId(22L).build();

	@BeforeEach
	void setup() {
		service = new MoveInService(photos, contracts, processor, provider);
	}

	void contract() {
		when(contracts.findById(1L)).thenReturn(Optional.of(Contract.builder().contractId(1L).tenant(tenant)
			.contractedAt(LocalDate.now(ZoneId.of("Asia/Seoul")).atStartOfDay())
			.startAt(LocalDateTime.now().plusMonths(2)).endAt(LocalDateTime.now().plusYears(2)).build()));
	}

	void ready() {
		contract();
		when(provider.getIfAvailable()).thenReturn(storage);
		when(processor.process(file)).thenReturn(new byte[]{(byte) 0xff, (byte) 0xd8});
	}

	void error(Runnable action, ErrorCode code) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(MoveInException.class,
			ex -> assertThat(ex.getErrorCode()).isEqualTo(code));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 6, 7})
	void includesContractDateAndWholeSeventhDate(int offset) {
		var date = LocalDate.of(2026, 11, 16);
		assertThatCode(() -> MoveInService.validateUploadDate(date, date.plusDays(offset))).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 8, 30})
	void rejectsBeforeContractAndAfterFinalDate(int offset) {
		var date = LocalDate.of(2026, 11, 16);
		error(() -> MoveInService.validateUploadDate(date, date.plusDays(offset)), ErrorCode.MOVEIN_OUTSIDE_UPLOAD_PERIOD);
	}

	@ParameterizedTest
	@ValueSource(longs = {0, -1})
	void rejectsNonPositiveContractId(long id) {
		error(() -> service.uploadPhotos(22L, id, List.of(file), List.of("")), ErrorCode.INVALID_REQUEST);
		verifyNoInteractions(contracts, provider);
	}

	@Test
	void rejectsMissingAndForeignContractBeforeStorage() {
		when(contracts.findById(1L)).thenReturn(Optional.empty());
		error(() -> service.uploadPhotos(22L, 1L, List.of(file), List.of("")), ErrorCode.CONTRACT_NOT_FOUND);
		contract();
		error(() -> service.uploadPhotos(99L, 1L, List.of(file), List.of("")), ErrorCode.ACCESS_DENIED);
		verifyNoInteractions(processor, storage, photos, provider);
	}

	@Test
	void rejectsEmptyOrTooManyFiles() {
		contract();
		for (List<MultipartFile> files : java.util.Arrays.<List<MultipartFile>>asList(null, List.of(),
				IntStream.range(0, 11).mapToObj(i -> (MultipartFile) file).toList())) {
			error(() -> service.uploadPhotos(22L, 1L, files, List.of("")), ErrorCode.INVALID_IMAGE);
		}
		verifyNoInteractions(provider, processor, photos);
	}

	@Test
	void rejectsMissingMismatchedNullAndTooLongCaptions() {
		contract();
		for (List<String> captions : java.util.Arrays.<List<String>>asList(null, List.of(),
				List.of("one", "two"), java.util.Collections.singletonList(null), List.of("가".repeat(256)))) {
			error(() -> service.uploadPhotos(22L, 1L, List.of(file), captions), ErrorCode.INVALID_REQUEST);
		}
		verifyNoInteractions(provider, processor, photos);
	}

	@Test
	void failsClosedWhenStorageNotConfigured() {
		contract();
		error(() -> service.uploadPhotos(22L, 1L, List.of(file), List.of("")), ErrorCode.MOVEIN_STORAGE_UNAVAILABLE);
		verifyNoInteractions(processor, photos);
	}

	@Test
	void validatesAllImagesBeforeAnyWrite() {
		contract();
		when(provider.getIfAvailable()).thenReturn(storage);
		when(processor.process(file)).thenReturn(new byte[]{1}).thenThrow(ImageException.invalidImage());
		assertThatThrownBy(() -> service.uploadPhotos(22L, 1L, List.of(file, file), List.of("a", "b")))
			.isInstanceOf(ImageException.class);
		verifyNoInteractions(storage, photos);
	}

	@Test
	@SuppressWarnings("unchecked")
	void preservesCaptionOrderAuthorAndDistinctServerKeysWithoutDeletingOnCommit() {
		ready();
		when(photos.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
		TransactionSynchronizationManager.initSynchronization();
		try {
			var result = service.uploadPhotos(22L, 1L, List.of(file, file), List.of("  벽\n얼룩  ", "가".repeat(255)));
			assertThat(result).extracting(MoveInDefectPhoto::getCaption).containsExactly("  벽\n얼룩  ", "가".repeat(255));
			assertThat(result).allSatisfy(photo -> {
				assertThat(photo.getUploadedBy()).isSameAs(tenant);
				assertThat(photo.getFileKey()).startsWith("movein-defect-photos/1/").endsWith(".jpg");
			});
			assertThat(result.get(0).getFileKey()).isNotEqualTo(result.get(1).getFileKey());
			complete(TransactionSynchronization.STATUS_COMMITTED);
			verify(storage, never()).delete(anyString());
		} finally { TransactionSynchronizationManager.clearSynchronization(); }
	}

	@Test
	void cleansBothAttemptedKeysAfterSecondWriteFailsEvenIfDeleteFails() {
		ready();
		doNothing().doThrow(new RuntimeException("timeout after put")).when(storage).write(anyString(), any());
		doThrow(new RuntimeException("delete down")).doNothing().when(storage).delete(anyString());
		TransactionSynchronizationManager.initSynchronization();
		try {
			error(() -> service.uploadPhotos(22L, 1L, List.of(file, file), List.of("", "")), ErrorCode.MOVEIN_STORAGE_UNAVAILABLE);
			assertThatCode(() -> complete(TransactionSynchronization.STATUS_ROLLED_BACK)).doesNotThrowAnyException();
			verify(storage, times(2)).delete(anyString());
			verifyNoInteractions(photos);
		} finally { TransactionSynchronizationManager.clearSynchronization(); }
	}

	@Test
	void cleansAllFilesAfterDatabaseFailureAndUnknownCompletion() {
		ready();
		when(photos.saveAllAndFlush(any())).thenThrow(new DataIntegrityViolationException("db failed"));
		TransactionSynchronizationManager.initSynchronization();
		try {
			assertThatThrownBy(() -> service.uploadPhotos(22L, 1L, List.of(file, file), List.of("a", "b")))
				.isInstanceOf(DataIntegrityViolationException.class);
			complete(TransactionSynchronization.STATUS_UNKNOWN);
			var written = ArgumentCaptor.forClass(String.class);
			var deleted = ArgumentCaptor.forClass(String.class);
			verify(storage, times(2)).write(written.capture(), any());
			verify(storage, times(2)).delete(deleted.capture());
			assertThat(deleted.getAllValues()).containsExactlyElementsOf(written.getAllValues());
		} finally { TransactionSynchronizationManager.clearSynchronization(); }
	}

	void complete(int status) {
		TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
	}
}
