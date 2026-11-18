package com.bogeum;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.aftersales.AfterSalesImageStorage;
import com.bogeum.domain.contract.model.Contract;
import com.bogeum.domain.movein.MoveInRepository;
import com.bogeum.domain.movein.MoveInService;
import com.bogeum.domain.movein.model.MoveInDefectPhoto;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.JwtUtil;
import tools.jackson.databind.json.JsonMapper;

/** 실제 Security·JWT·JPA로 계약별 조회를 검증하며 외부 저장소만 Mock으로 대체한다 (MOVEIN_002). */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-movein-read;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "spring.jpa.open-in-view=false",
	"spring.sql.init.mode=never", "spring.cloud.aws.s3.enabled=false", "auth.cookie-secure=false",
	"solapi.api-key=TEST_UNUSED", "solapi.api-secret=TEST_UNUSED", "solapi.sender-number=01000000000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MoveInReadIntegrationTests {
	@MockitoBean AfterSalesImageStorage storage;
	@MockitoSpyBean MoveInRepository photos;
	@MockitoSpyBean MoveInService service;
	private final MockMvc mvc;
	private final EntityManager em;
	private final JdbcTemplate jdbc;
	private final JwtUtil jwt;
	private final JsonMapper mapper;
	private final TransactionTemplate tx;

	@Autowired
	MoveInReadIntegrationTests(MockMvc mvc, EntityManager em, JdbcTemplate jdbc, JwtUtil jwt,
			JsonMapper mapper, PlatformTransactionManager manager) {
		this.mvc = mvc; this.em = em; this.jdbc = jdbc; this.jwt = jwt; this.mapper = mapper;
		this.tx = new TransactionTemplate(manager);
	}

	record Fixture(long contractId, long landlordId, long tenantId, long currentOwnerId) {}

	static User user(EntityManager em, Role role) {
		var user = User.builder().email(UUID.randomUUID() + "@example.test").passwordHash("test-unused")
			.name("조회 테스트").phone("01000000000").role(role).build();
		em.persist(user);
		return user;
	}

	static Fixture fixture(EntityManager em, TransactionTemplate tx, Role landlordRole, boolean changedOwner) {
		return tx.execute(status -> {
			var landlord = user(em, landlordRole);
			var tenant = user(em, Role.USER);
			var owner = changedOwner ? user(em, Role.USER) : landlord;
			var agent = user(em, Role.AGENT);
			var property = Property.builder().landlord(owner).agent(agent).leaseType(LeaseType.MONTHLY)
				.deposit(BigDecimal.TEN).monthlyRent(BigDecimal.ONE).roadAddress("서울 입주 조회로 113").build();
			em.persist(property);
			var contract = Contract.builder().property(property).landlord(landlord).tenant(tenant).agent(agent)
				.leaseType(LeaseType.MONTHLY).deposit(BigDecimal.TEN).monthlyRent(BigDecimal.ONE).paymentDay(1)
				.startAt(LocalDateTime.now().minusYears(2)).endAt(LocalDateTime.now().minusYears(1))
				.contractedAt(LocalDateTime.now().minusYears(3)).brokerageFee(BigDecimal.ONE).build();
			em.persist(contract); em.flush();
			return new Fixture(contract.getContractId(), landlord.getUserId(), tenant.getUserId(), owner.getUserId());
		});
	}

	static long photo(EntityManager em, TransactionTemplate tx, long contractId, String caption) {
		return tx.execute(status -> {
			var contract = em.find(Contract.class, contractId);
			var saved = MoveInDefectPhoto.builder().contract(contract).uploadedBy(contract.getTenant())
				.caption(caption).fileKey("movein-defect-photos/" + contractId + "/" + UUID.randomUUID() + ".jpg").build();
			em.persist(saved); em.flush();
			return saved.getMoveInDefectPhotoId();
		});
	}

	String token(long userId) { return "Bearer " + jwt.createAccess(userId, UUID.randomUUID().toString()); }
	String path(long contractId) { return "/contract/" + contractId + "/movein-defects"; }
	Fixture fixture() { return fixture(em, tx, Role.USER, false); }
	long photo(Fixture fixture, String caption) { return photo(em, tx, fixture.contractId(), caption); }

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 12})
	void readsOnlyThisContractsAllPhotosLongAfterUploadPeriodAndWithoutCsrf(int count) throws Exception {
		var f = fixture();
		var ids = IntStream.range(0, count).mapToLong(index -> photo(f, index == 0 ? null : "설명 " + index)).boxed().toList();
		var other = fixture();
		photo(other, "다른 계약의 비공개 사진");
		if (count > 0) when(storage.createReadUrl(anyString())).thenReturn("https://example.test/private-photo?signature");
		var response = mvc.perform(get(path(f.contractId())).header("Authorization", token(f.landlordId())))
			.andExpect(status().isOk()).andExpect(jsonPath("$.contractId").value(f.contractId()))
			.andExpect(jsonPath("$.photos.length()").value(count)).andReturn().getResponse();
		var body = mapper.readTree(response.getContentAsString());
		assertThat(body.size()).isEqualTo(2);
		for (int index = 0; index < count; index++) {
			var item = body.get("photos").get(index);
			assertThat(item.size()).isEqualTo(4);
			assertThat(item.get("moveInDefectPhotoId").asLong()).isEqualTo(ids.get(index));
			assertThat(item.get("url").asText()).isEqualTo("https://example.test/private-photo?signature");
			assertThat(item.get("createdAt").asText()).isNotBlank();
			if (index == 0) assertThat(item.get("caption").isNull()).isTrue();
			else assertThat(item.get("caption").asText()).isEqualTo("설명 " + index);
		}
		assertThat(response.getContentAsString()).doesNotContain("fileKey", "uploadedBy", "email", "phone", "passwordHash", "다른 계약");
		verify(storage, times(count)).createReadUrl(anyString());
		if (count == 0) verifyNoInteractions(storage);
		verify(storage, never()).write(anyString(), any());
		verify(storage, never()).delete(anyString());
	}

	@Test
	void sortsByCreatedAtThenIdDeterministically() throws Exception {
		var f = fixture();
		long first = photo(f, "같은 시각의 작은 ID"), second = photo(f, "같은 시각의 큰 ID"), oldest = photo(f, "먼저 생성됨");
		var tied = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
		jdbc.update("update movein_defect_photo set created_at=? where movein_defect_photo_id in (?,?)", tied, first, second);
		jdbc.update("update movein_defect_photo set created_at=? where movein_defect_photo_id=?", tied.minusDays(1), oldest);
		when(storage.createReadUrl(anyString())).thenReturn("https://example.test/signed");
		for (int attempt = 0; attempt < 2; attempt++) {
			mvc.perform(get(path(f.contractId())).header("Authorization", token(f.landlordId())))
				.andExpect(status().isOk()).andExpect(jsonPath("$.photos[0].moveInDefectPhotoId").value(oldest))
				.andExpect(jsonPath("$.photos[1].moveInDefectPhotoId").value(first))
				.andExpect(jsonPath("$.photos[2].moveInDefectPhotoId").value(second));
		}
	}

	@Test
	void usesContractLandlordRatherThanPropertyCurrentOwner() throws Exception {
		var f = fixture(em, tx, Role.USER, true);
		photo(f, "계약에 속한 사진");
		mvc.perform(get(path(f.contractId())).header("Authorization", token(f.currentOwnerId())))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		verifyNoInteractions(storage);
		when(storage.createReadUrl(anyString())).thenReturn("https://example.test/signed");
		mvc.perform(get(path(f.contractId())).header("Authorization", token(f.landlordId())))
			.andExpect(status().isOk()).andExpect(jsonPath("$.photos.length()").value(1));
	}

	@Test
	void rejectsOtherContractsLandlordAndTenantBeforeIssuingUrls() throws Exception {
		var f = fixture(); var other = fixture();
		photo(f, "보호할 사진");
		for (long userId : List.of(f.tenantId(), other.landlordId())) {
			mvc.perform(get(path(f.contractId())).header("Authorization", token(userId)))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		}
		verifyNoInteractions(storage);
	}

	@ParameterizedTest
	@EnumSource(value = Role.class, names = {"ADMIN", "AGENT"})
	void rejectsNonUserRoleEvenWhenNamedAsContractLandlord(Role role) throws Exception {
		var f = fixture(em, tx, role, false);
		photo(f, "역할로 차단할 사진");
		mvc.perform(get(path(f.contractId())).header("Authorization", token(f.landlordId())))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		verifyNoInteractions(storage);
	}

	@Test
	void rejectsMissingOrInvalidJwtBeforeStorage() throws Exception {
		var f = fixture(); photo(f, "보호할 사진");
		mvc.perform(get(path(f.contractId()))).andExpect(status().isUnauthorized());
		mvc.perform(get(path(f.contractId())).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
		verifyNoInteractions(storage);
	}

	@Test
	void handlesMissingAndInvalidContractIdsWithoutStorage() throws Exception {
		var f = fixture();
		mvc.perform(get(path(Long.MAX_VALUE)).header("Authorization", token(f.landlordId())))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"));
		for (String id : List.of("0", "-1", "abc", "9223372036854775808")) {
			mvc.perform(get("/contract/" + id + "/movein-defects").header("Authorization", token(f.landlordId())))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
		verifyNoInteractions(storage);
	}

	@Test
	void headRequiresValidAuthenticationBeforeReadService() throws Exception {
		var f = fixture(); photo(f, "보호할 사진");
		clearInvocations(service, photos, storage);
		for (long id : List.of(f.contractId(), Long.MAX_VALUE)) {
			mvc.perform(head(path(id))).andExpect(status().isUnauthorized());
			mvc.perform(head(path(id)).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
		}
		verifyNoInteractions(service, photos, storage);
	}

	@ParameterizedTest
	@EnumSource(value = Role.class, names = {"ADMIN", "AGENT"})
	void headRejectsNonUserBeforeReadServiceRegardlessOfContractExistence(Role role) throws Exception {
		// 계약 관계까지 있어도 USER가 아니면 MVC 핸들러에 도달하면 안 된다.
		var f = fixture(em, tx, role, false); photo(f, "역할로 차단할 사진");
		clearInvocations(service, photos, storage);
		for (long id : List.of(f.contractId(), Long.MAX_VALUE)) {
			mvc.perform(head(path(id)).header("Authorization", token(f.landlordId())))
				.andExpect(status().isForbidden());
		}
		verifyNoInteractions(service, photos, storage);
	}

	@Test
	void headAllowsContractLandlordThroughRealReadHandler() throws Exception {
		var f = fixture();
		long photoId = photo(f, "HEAD 권한 확인");
		String key = jdbc.queryForObject("select file_key from movein_defect_photo where movein_defect_photo_id=?", String.class, photoId);
		when(storage.createReadUrl(key)).thenReturn("https://example.test/head-photo");
		clearInvocations(service, photos, storage);
		mvc.perform(head(path(f.contractId())).header("Authorization", token(f.landlordId())))
			.andExpect(status().isOk());
		verify(service).readPhotos(f.landlordId(), f.contractId());
		verify(photos).findAllByContract_ContractIdOrderByCreatedAtAscMoveInDefectPhotoIdAsc(f.contractId());
		verify(storage).createReadUrl(key);
	}

	@Test
	void headRejectsTenantAndOtherContractsLandlordBeforeReadingPhotos() throws Exception {
		var f = fixture(); var other = fixture();
		photo(f, "보호할 사진");
		clearInvocations(service, photos, storage);
		for (long userId : List.of(f.tenantId(), other.landlordId())) {
			mvc.perform(head(path(f.contractId())).header("Authorization", token(userId)))
				.andExpect(status().isForbidden());
			verify(service).readPhotos(userId, f.contractId());
		}
		verifyNoInteractions(photos, storage);
	}

	@Test
	void urlFailureIs503WithoutPartialPhotosOrInternalError() throws Exception {
		var f = fixture(); photo(f, "첫 사진"); photo(f, "두 번째 사진");
		when(storage.createReadUrl(anyString())).thenReturn("https://example.test/signed")
			.thenThrow(new IllegalStateException("private-key credential-detail"));
		var response = mvc.perform(get(path(f.contractId())).header("Authorization", token(f.landlordId())))
			.andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("MOVEIN_READ_URL_UNAVAILABLE"))
			.andExpect(jsonPath("$.message").value("사진 조회 주소를 만들 수 없습니다. 잠시 후 다시 시도해 주세요."))
			.andExpect(jsonPath("$.photos").doesNotExist()).andReturn().getResponse();
		assertThat(response.getContentAsString()).doesNotContain("private-key", "credential-detail", "fileKey", "https://");
		verify(storage, times(2)).createReadUrl(anyString());
	}

	@Test
	void uploadedPhotoCanBeReadByLandlordWithoutChangingPostResponse() throws Exception {
		var f = fixture();
		jdbc.update("update contract set contracted_at=? where contract_id=?", LocalDateTime.now(), f.contractId());
		var issued = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
		var csrf = mapper.readTree(issued.getContentAsString());
		var bytes = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "PNG", bytes);
		mvc.perform(multipart(path(f.contractId())).header("Authorization", token(f.tenantId()))
			.cookie(issued.getCookie("BOGEUM_CSRF")).header(csrf.get("headerName").asText(), csrf.get("token").asText())
			.file(new MockMultipartFile("request", "", "application/json", mapper.writeValueAsBytes(Map.of("captions", List.of("입주 기록")))))
			.file(new MockMultipartFile("images", "defect.png", "image/png", bytes.toByteArray())))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.photos[0].caption").value("입주 기록"))
			.andExpect(jsonPath("$.photos[0].url").doesNotExist());
		when(storage.createReadUrl(anyString())).thenReturn("https://example.test/new-photo");
		mvc.perform(get(path(f.contractId())).header("Authorization", token(f.landlordId())))
			.andExpect(status().isOk()).andExpect(jsonPath("$.photos[0].caption").value("입주 기록"))
			.andExpect(jsonPath("$.photos[0].url").value("https://example.test/new-photo"));
	}
}

/** 저장소 Bean이 실제로 없는 Spring 컨텍스트에서도 메타데이터 조회 계약을 유지한다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-movein-read-no-storage;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "spring.jpa.open-in-view=false",
	"spring.sql.init.mode=never", "spring.cloud.aws.s3.enabled=false", "auth.cookie-secure=false",
	"solapi.api-key=TEST_UNUSED", "solapi.api-secret=TEST_UNUSED", "solapi.sender-number=01000000000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MoveInReadNoStorageIntegrationTests {
	private final MockMvc mvc;
	private final EntityManager em;
	private final JwtUtil jwt;
	private final TransactionTemplate tx;
	private final ObjectProvider<AfterSalesImageStorage> storage;

	@Autowired
	MoveInReadNoStorageIntegrationTests(MockMvc mvc, EntityManager em, JwtUtil jwt,
			PlatformTransactionManager manager, ObjectProvider<AfterSalesImageStorage> storage) {
		this.mvc = mvc; this.em = em; this.jwt = jwt; this.tx = new TransactionTemplate(manager); this.storage = storage;
	}

	@Test
	void returnsNullUrlAndNullCaptionWithoutAnyStorageBean() throws Exception {
		assertThat(storage.getIfAvailable()).isNull();
		var f = MoveInReadIntegrationTests.fixture(em, tx, Role.USER, false);
		long photoId = MoveInReadIntegrationTests.photo(em, tx, f.contractId(), null);
		var response = mvc.perform(get("/contract/" + f.contractId() + "/movein-defects")
			.header("Authorization", "Bearer " + jwt.createAccess(f.landlordId(), UUID.randomUUID().toString())))
			.andExpect(status().isOk()).andExpect(jsonPath("$.contractId").value(f.contractId()))
			.andExpect(jsonPath("$.photos[0].moveInDefectPhotoId").value(photoId)).andReturn().getResponse();
		assertThat(response.getContentAsString()).contains("\"caption\":null", "\"url\":null")
			.doesNotContain("fileKey", "uploadedBy", "email", "phone", "passwordHash");
	}
}
