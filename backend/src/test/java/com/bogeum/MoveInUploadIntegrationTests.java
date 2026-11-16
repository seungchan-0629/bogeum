package com.bogeum;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

/** 실제 Security·JWT·CSRF·H2/JPA를 사용하고 외부 사진 저장소만 Mock으로 대체한다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-movein-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "spring.jpa.open-in-view=false",
	"spring.sql.init.mode=never", "auth.cookie-secure=false",
	"solapi.api-key=TEST_UNUSED", "solapi.api-secret=TEST_UNUSED", "solapi.sender-number=01000000000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MoveInUploadIntegrationTests {
	@MockitoBean AfterSalesImageStorage storage;
	@MockitoSpyBean MoveInRepository photos;
	final MockMvc mvc;
	final EntityManager em;
	final JdbcTemplate jdbc;
	final JsonMapper mapper;
	final JwtUtil jwt;
	final MoveInService service;
	final TransactionTemplate tx;
	final Map<String, byte[]> objects = new ConcurrentHashMap<>();

	@Autowired
	MoveInUploadIntegrationTests(MockMvc mvc, EntityManager em, JdbcTemplate jdbc, JsonMapper mapper,
			JwtUtil jwt, MoveInService service, PlatformTransactionManager manager) {
		this.mvc = mvc; this.em = em; this.jdbc = jdbc; this.mapper = mapper;
		this.jwt = jwt; this.service = service; this.tx = new TransactionTemplate(manager);
	}

	@BeforeEach
	void storage() {
		objects.clear();
		doAnswer(invocation -> {
			objects.put(invocation.getArgument(0), ((byte[]) invocation.getArgument(1)).clone());
			return null;
		}).when(storage).write(anyString(), any());
		doAnswer(invocation -> { objects.remove(invocation.getArgument(0)); return null; }).when(storage).delete(anyString());
	}

	record Fixture(long contractId, long tenantId, long landlordId, long agentId, long legacyPhotoId) {}

	Fixture fixture(int age, Role tenantRole) {
		return tx.execute(status -> {
			User tenant = user(tenantRole), landlord = user(Role.USER), agent = user(Role.AGENT);
			var property = Property.builder().landlord(landlord).agent(agent).leaseType(LeaseType.MONTHLY)
				.deposit(BigDecimal.TEN).monthlyRent(BigDecimal.ONE).roadAddress("서울 입주로 111").build();
			em.persist(property);
			// 시작일·등록일과 무관하게 계약일만 업로드 기한의 기준이다.
			var contract = Contract.builder().property(property).tenant(tenant).landlord(landlord).agent(agent)
				.leaseType(LeaseType.MONTHLY).deposit(BigDecimal.TEN).monthlyRent(BigDecimal.ONE).paymentDay(1)
				.startAt(LocalDateTime.now().plusMonths(2)).endAt(LocalDateTime.now().plusYears(2))
				.brokerageFee(BigDecimal.ONE).contractedAt(LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(age).atStartOfDay()).build();
			em.persist(contract);
			var legacy = MoveInDefectPhoto.builder().contract(contract).uploadedBy(tenant).fileKey("existing-protected").build();
			em.persist(legacy); em.flush();
			return new Fixture(contract.getContractId(), tenant.getUserId(), landlord.getUserId(), agent.getUserId(),
				legacy.getMoveInDefectPhotoId());
		});
	}

	User user(Role role) {
		var user = User.builder().email(UUID.randomUUID()+"@example.test").passwordHash("test-unused")
			.name("테스트").phone("01000000000").role(role).build();
		em.persist(user);
		return user;
	}

	String path(long contractId) { return "/contract/"+contractId+"/movein-defects"; }
	String token(long userId) { return "Bearer "+jwt.createAccess(userId, UUID.randomUUID().toString()); }
	long count(long contractId) { return jdbc.queryForObject("select count(*) from movein_defect_photo where contract_id=?", Long.class, contractId); }

	MockMultipartHttpServletRequestBuilder request(long contractId, Long userId, Object captions, boolean csrf) throws Exception {
		var request = multipart(path(contractId));
		if (captions != null) request.file(new MockMultipartFile("request", "", "application/json",
			mapper.writeValueAsBytes(Map.of("captions", captions, "uploadedBy", 999999, "contractId", 999999))));
		if (userId != null) request.header("Authorization", token(userId));
		if (csrf) {
			var issued = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
			var data = mapper.readTree(issued.getContentAsString());
			request.cookie(issued.getCookie("BOGEUM_CSRF")).header(data.get("headerName").asText(), data.get("token").asText());
		}
		return request;
	}

	MockMultipartFile image(String format) throws Exception {
		var out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), format, out);
		return new MockMultipartFile("images", "ignored.txt", "text/plain", out.toByteArray());
	}

	@Test
	void storesRealJpegAndPngWithOrderedCaptionsAndNoKeysInResponse() throws Exception {
		var f = fixture(0, Role.USER);
		var result = mvc.perform(request(f.contractId(), f.tenantId(), List.of("  벽\n얼룩  ", "가".repeat(255)), true)
				.file(image("PNG")).file(image("JPEG")))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.contractId").value(f.contractId()))
			.andExpect(jsonPath("$.photos.length()").value(2)).andExpect(jsonPath("$.photos[0].caption").value("  벽\n얼룩  "))
			.andExpect(jsonPath("$.photos[1].caption").value("가".repeat(255)))
			.andExpect(jsonPath("$.photos[0].createdAt").isNotEmpty())
			.andExpect(jsonPath("$.photos[0].moveInDefectPhotoId").isNumber()).andReturn();
		var tree = mapper.readTree(result.getResponse().getContentAsString());
		for (int i=0;i<2;i++) {
			long id = tree.get("photos").get(i).get("moveInDefectPhotoId").asLong();
			assertThat(jdbc.queryForObject("select uploaded_by from movein_defect_photo where movein_defect_photo_id=?", Long.class,id)).isEqualTo(f.tenantId());
		}
		assertThat(result.getResponse().getContentAsString()).doesNotContain("fileKey","uploadedBy","password","email","movein-defect-photos/");
		assertThat(count(f.contractId())).isEqualTo(3);
		assertThat(jdbc.queryForObject("select caption from movein_defect_photo where movein_defect_photo_id=?",String.class,f.legacyPhotoId())).isNull();
		assertThat(objects).hasSize(2);
		for (var entry:objects.entrySet()) {
			assertThat(entry.getKey()).startsWith("movein-defect-photos/"+f.contractId()+"/");
			assertThat(ImageIO.read(new java.io.ByteArrayInputStream(entry.getValue()))).isNotNull();
			assertThat(entry.getValue()[0] & 0xff).isEqualTo(0xff);
		}
		verify(storage, never()).createReadUrl(anyString());
		verify(storage, never()).delete(anyString());
	}

	@Test
	void permitsAdditionalRepeatedAndConcurrentUploadsBeyondTenTotal() throws Exception {
		var f = fixture(0, Role.USER);
		var request = request(f.contractId(),f.tenantId(),Collections.nCopies(10,""),true);
		for (int i=0;i<10;i++) request.file(image("PNG"));
		mvc.perform(request).andExpect(status().isCreated());
		var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
		try {
			var first = executor.submit(() -> service.uploadPhotos(f.tenantId(),f.contractId(),List.of(image("PNG")),List.of("same")));
			var second = executor.submit(() -> service.uploadPhotos(f.tenantId(),f.contractId(),List.of(image("PNG")),List.of("same")));
			var ids = List.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS).get(0).getMoveInDefectPhotoId(),
				second.get(10,java.util.concurrent.TimeUnit.SECONDS).get(0).getMoveInDefectPhotoId());
			assertThat(ids.get(0)).isNotEqualTo(ids.get(1));
		} finally { executor.shutdownNow(); }
		assertThat(count(f.contractId())).isEqualTo(13);
		assertThat(objects).hasSize(12);
	}

	@ParameterizedTest
	@ValueSource(ints={0,7})
	void acceptsBothBoundaryDatesIgnoringStartAndCreatedDates(int age) throws Exception {
		var f=fixture(age,Role.USER);
		mvc.perform(request(f.contractId(),f.tenantId(),List.of(""),true).file(image("PNG"))).andExpect(status().isCreated());
	}

	@ParameterizedTest
	@ValueSource(ints={-1,8,365})
	void rejectsFutureAndExpiredContractDates(int age) throws Exception {
		var f=fixture(age,Role.USER);
		mvc.perform(request(f.contractId(),f.tenantId(),List.of(""),true).file(image("PNG")))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOVEIN_OUTSIDE_UPLOAD_PERIOD"));
		assertThat(count(f.contractId())).isEqualTo(1); verifyNoInteractions(storage);
	}

	@ParameterizedTest
	@EnumSource(Role.class)
	void permitsOnlyUserRoleEvenWhenOtherRoleIsContractTenant(Role role) throws Exception {
		var f=fixture(0,role);
		mvc.perform(request(f.contractId(),f.tenantId(),List.of(""),true).file(image("PNG")))
			.andExpect(status().is(role==Role.USER?201:403));
	}

	@Test
	void rejectsForeignTenantLandlordAndMissingContract() throws Exception {
		var f=fixture(0,Role.USER); var other=fixture(0,Role.USER);
		for(long id:List.of(f.landlordId(),other.tenantId())) {
			mvc.perform(request(f.contractId(),id,List.of(""),true).file(image("PNG")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		}
		mvc.perform(request(Long.MAX_VALUE,f.tenantId(),List.of(""),true).file(image("PNG")))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"));
		verifyNoInteractions(storage);
	}

	@Test
	void rejectsUnauthenticatedInvalidJwtAndMissingCsrf() throws Exception {
		var f=fixture(0,Role.USER);
		mvc.perform(request(f.contractId(),null,List.of(""),true).file(image("PNG"))).andExpect(status().isUnauthorized());
		mvc.perform(request(f.contractId(),null,List.of(""),true).header("Authorization","Bearer invalid").file(image("PNG")))
			.andExpect(status().isUnauthorized());
		mvc.perform(request(f.contractId(),f.tenantId(),List.of(""),false).file(image("PNG")))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		verifyNoInteractions(storage);
	}

	@Test
	void rejectsInvalidContractIdAndMissingRequest() throws Exception {
		var f=fixture(0,Role.USER);
		for (String id:List.of("0","-1","abc","9223372036854775808")) {
			var req=request(f.contractId(),f.tenantId(),List.of(""),true).file(image("PNG"));
			req.with(r->{r.setRequestURI("/contract/"+id+"/movein-defects");return r;});
			mvc.perform(req).andExpect(status().isBadRequest());
		}
		mvc.perform(request(f.contractId(),f.tenantId(),null,true).file(image("PNG"))).andExpect(status().isBadRequest());
		verifyNoInteractions(storage);
	}

	@Test
	void rejectsCaptionCountNullAndLengthWithoutWriting() throws Exception {
		var f=fixture(0,Role.USER);
		for (Object captions:List.of(List.of(),List.of("one","two"),Collections.singletonList(null),List.of("가".repeat(256)))) {
			mvc.perform(request(f.contractId(),f.tenantId(),captions,true).file(image("PNG"))).andExpect(status().isBadRequest());
		}
		mvc.perform(request(f.contractId(),f.tenantId(),List.of(""),true)).andExpect(status().isBadRequest());
		var tooMany=request(f.contractId(),f.tenantId(),Collections.nCopies(11,""),true);
		for(int i=0;i<11;i++)tooMany.file(image("PNG"));
		mvc.perform(tooMany).andExpect(status().isBadRequest());
		verifyNoInteractions(storage);
	}

	@Test
	void rejectsEmptyFakeGifOversizeAndPixelBombBeforeWriting() throws Exception {
		var f=fixture(0,Role.USER);
		byte[] bomb=image("PNG").getBytes().clone();
		ByteBuffer.wrap(bomb).putInt(16,10000).putInt(20,5000);
		var crc=new CRC32();crc.update(bomb,12,17);ByteBuffer.wrap(bomb).putInt(29,(int)crc.getValue());
		for(var bad:List.of(new MockMultipartFile("images",new byte[0]),
				new MockMultipartFile("images","fake.jpg","image/jpeg","fake".getBytes()),
				image("GIF"),new MockMultipartFile("images",new byte[20971521]),new MockMultipartFile("images",bomb))) {
			mvc.perform(request(f.contractId(),f.tenantId(),List.of("valid","bad"),true).file(image("PNG")).file(bad))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
		}
		assertThat(count(f.contractId())).isEqualTo(1);verifyNoInteractions(storage);
	}

	@Test
	void rollsBackAllMetadataAndFilesOnPartialExternalFailure() throws Exception {
		var f=fixture(0,Role.USER);var writes=new AtomicInteger();
		objects.put("existing-protected",new byte[]{1});
		doAnswer(invocation->{
			objects.put(invocation.getArgument(0),invocation.getArgument(1));
			if(writes.incrementAndGet()==2)throw new RuntimeException("timeout after write");
			return null;
		}).when(storage).write(anyString(),any());
		mvc.perform(request(f.contractId(),f.tenantId(),List.of("a","b"),true).file(image("PNG")).file(image("PNG")))
			.andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("MOVEIN_STORAGE_UNAVAILABLE"));
		assertThat(count(f.contractId())).isEqualTo(1);
		assertThat(objects).containsOnlyKeys("existing-protected");
		verify(storage,times(2)).delete(anyString());
	}

	@Test
	@SuppressWarnings("unchecked")
	void rollsBackPartiallyInsertedMetadataAndCleansFilesOnDatabaseFailure() throws Exception {
		var f=fixture(0,Role.USER);
		doAnswer(invocation->{
			List<MoveInDefectPhoto> values=invocation.getArgument(0);
			em.persist(values.get(0));em.flush();
			throw new DataIntegrityViolationException("forced DB failure after first row");
		}).when(photos).saveAllAndFlush(any());
		assertThatThrownBy(()->service.uploadPhotos(f.tenantId(),f.contractId(),List.of(image("PNG"),image("PNG")),List.of("a","b")))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(count(f.contractId())).isEqualTo(1);assertThat(objects).isEmpty();
		verify(storage,times(2)).delete(anyString());
	}

	@Test
	void cleansFilesIfOuterTransactionFailsAtCommit() throws Exception {
		var f=fixture(0,Role.USER);var file=image("PNG");
		assertThatThrownBy(()->tx.execute(status->{
			service.uploadPhotos(f.tenantId(),f.contractId(),List.of(file),List.of("a"));
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
				@Override public void beforeCommit(boolean readOnly){throw new IllegalStateException("commit rejected");}
			});
			return null;
		})).isInstanceOf(IllegalStateException.class);
		assertThat(count(f.contractId())).isEqualTo(1);assertThat(objects).isEmpty();
		verify(storage).delete(anyString());
	}
}
