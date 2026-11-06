package com.bogeum;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.bogeum.common.enums.AsStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.Role;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.aftersales.AfterSalesCommentRepository;
import com.bogeum.domain.aftersales.AfterSalesException;
import com.bogeum.domain.aftersales.AfterSalesRepository;
import com.bogeum.domain.aftersales.AfterSalesService;
import com.bogeum.domain.aftersales.model.AfterSalesComment;
import com.bogeum.domain.aftersales.model.AfterSalesDto;
import com.bogeum.domain.aftersales.model.AfterSalesRequest;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import tools.jackson.databind.json.JsonMapper;

/** 실제 JWT·CSRF·H2로 댓글·상태 변경과 종료 정책을 검증한다. 외부 저장소와 알림은 사용하지 않는다 (AS_003). */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-as-response-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "spring.jpa.open-in-view=false",
	"spring.sql.init.mode=never", "auth.cookie-secure=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AfterSalesResponseIntegrationTests {
	private final MockMvc mvc;
	private final UserRepository users;
	private final PasswordEncoder encoder;
	private final UserService auth;
	private final PropertyRepository properties;
	private final AfterSalesRepository requests;
	private final AfterSalesCommentRepository comments;
	private final AfterSalesService service;
	private final JdbcTemplate jdbc;
	private final JsonMapper mapper;
	private final PlatformTransactionManager transactionManager;

	@Autowired
	AfterSalesResponseIntegrationTests(MockMvc mvc, UserRepository users, PasswordEncoder encoder, UserService auth,
			PropertyRepository properties, AfterSalesRepository requests, AfterSalesCommentRepository comments,
			AfterSalesService service, JdbcTemplate jdbc, JsonMapper mapper, PlatformTransactionManager transactionManager) {
		this.mvc = mvc;
		this.users = users;
		this.encoder = encoder;
		this.auth = auth;
		this.properties = properties;
		this.requests = requests;
		this.comments = comments;
		this.service = service;
		this.jdbc = jdbc;
		this.mapper = mapper;
		this.transactionManager = transactionManager;
	}

	@Test
	void createsTrimmedCommentWithServerAuthorAndPreservesStatus() throws Exception {
		var fixture = fixture(AsStatus.CHECKING);
		var result = mvc.perform(csrfWrite(path(fixture, "comments"), "comments", true)
			.header("Authorization", "Bearer " + token(fixture.owner()))
			.content(mapper.writeValueAsString(Map.of("content", "  기사 방문 예정  ", "authorId", fixture.tenant().user().getUserId(),
				"landlordId", fixture.tenant().user().getUserId()))))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.asCommentId").isNumber())
			.andExpect(jsonPath("$.asRequestId").value(fixture.request().getAsRequestId().intValue()))
			.andExpect(jsonPath("$.authorName").value("임대인")).andExpect(jsonPath("$.content").value("기사 방문 예정"))
			.andExpect(jsonPath("$.createdAt").isNotEmpty()).andReturn();
		long id = mapper.readTree(result.getResponse().getContentAsString()).get("asCommentId").asLong();
		var saved = comments.findById(id).orElseThrow();
		assertThat(saved.getAuthor().getUserId()).isEqualTo(fixture.owner().user().getUserId());
		assertThat(saved.getContent()).isEqualTo("기사 방문 예정");
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(requests.findById(fixture.request().getAsRequestId()).orElseThrow().getAsStatus()).isEqualTo(AsStatus.CHECKING);
		assertThat(result.getResponse().getContentAsString()).doesNotContain("email", "phone", "passwordHash", "\"author\"", "fileKey");
	}

	@Test
	void readsOnlyThisRequestsCommentsInStableOrder() throws Exception {
		var fixture = fixture(AsStatus.RECEIVED);
		var another = requests.save(AfterSalesRequest.builder().property(fixture.property()).tenant(fixture.tenant().user())
			.content("같은 매물의 다른 접수").build());
		var foreign = fixture(AsStatus.RECEIVED);
		var first = comment(fixture.request(), fixture.owner().user(), "나중 등록 시각");
		var second = comment(fixture.request(), fixture.owner().user(), "먼저 등록한 첫 댓글");
		var third = comment(fixture.request(), fixture.owner().user(), "같은 시각의 다음 댓글");
		comment(another, fixture.owner().user(), "다른 접수의 비공개 댓글");
		comment(foreign.request(), foreign.owner().user(), "타인 매물의 비공개 댓글");
		var createdAt = LocalDateTime.of(2026, 11, 1, 10, 0);
		jdbc.update("update as_comment set created_at = ? where as_request_id = ?", createdAt, fixture.request().getAsRequestId());
		jdbc.update("update as_comment set created_at = ? where as_comment_id = ?", createdAt.plusHours(1), first.getAsCommentId());

		var result = mvc.perform(get("/as-requests/" + fixture.request().getAsRequestId())
			.header("Authorization", "Bearer " + token(fixture.owner()))).andExpect(status().isOk())
			.andExpect(jsonPath("$.comments.length()").value(3))
			.andExpect(jsonPath("$.comments[0].asCommentId").value(second.getAsCommentId().intValue()))
			.andExpect(jsonPath("$.comments[1].asCommentId").value(third.getAsCommentId().intValue()))
			.andExpect(jsonPath("$.comments[2].asCommentId").value(first.getAsCommentId().intValue()))
			.andExpect(jsonPath("$.comments[0].authorName").value("임대인"))
			.andExpect(jsonPath("$.images").isEmpty()).andReturn();
		assertThat(result.getResponse().getContentAsString())
			.doesNotContain("다른 접수의 비공개 댓글", "타인 매물의 비공개 댓글", "email", "phone", "passwordHash");
	}

	@Test
	void returnsEmptyCommentsInExistingDetail() throws Exception {
		var fixture = fixture(AsStatus.RECEIVED);
		mvc.perform(get("/as-requests/" + fixture.request().getAsRequestId())
			.header("Authorization", "Bearer " + token(fixture.owner()))).andExpect(status().isOk())
			.andExpect(jsonPath("$.comments").isArray()).andExpect(jsonPath("$.comments").isEmpty())
			.andExpect(jsonPath("$.content").value("누수 점검")).andExpect(jsonPath("$.asStatus").value("RECEIVED"));
	}

	@ParameterizedTest
	@EnumSource(AsStatus.class)
	void persistsStatusChangesAndKeepsExistingComments(AsStatus target) throws Exception {
		var fixture = fixture(AsStatus.CHECKING);
		var comment = comment(fixture.request(), fixture.owner().user(), "기존 조치 사항");
		mvc.perform(csrfWrite(path(fixture, "status"), "status", true)
			.header("Authorization", "Bearer " + token(fixture.owner()))
			.content(mapper.writeValueAsString(Map.of("asStatus", target.name())))).andExpect(status().isOk())
			.andExpect(jsonPath("$.asRequestId").value(fixture.request().getAsRequestId().intValue()))
			.andExpect(jsonPath("$.asStatus").value(target.name()));
		assertThat(requests.findById(fixture.request().getAsRequestId()).orElseThrow().getAsStatus()).isEqualTo(target);
		assertThat(comments.findById(comment.getAsCommentId()).orElseThrow().getContent()).isEqualTo("기존 조치 사항");
		mvc.perform(get("/as-requests/" + fixture.request().getAsRequestId())
			.header("Authorization", "Bearer " + token(fixture.owner()))).andExpect(status().isOk())
			.andExpect(jsonPath("$.asStatus").value(target.name())).andExpect(jsonPath("$.comments.length()").value(1));
	}

	@Test
	void allowsForwardAndBackwardChangesBeforeCompletion() throws Exception {
		var fixture = fixture(AsStatus.RECEIVED);
		var token = token(fixture.owner());
		for (AsStatus next : List.of(AsStatus.IN_PROGRESS, AsStatus.CHECKING, AsStatus.RECEIVED)) {
			mvc.perform(csrfWrite(path(fixture, "status"), "status", true).header("Authorization", "Bearer " + token)
				.content(mapper.writeValueAsString(Map.of("asStatus", next.name()))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.asStatus").value(next.name()));
			assertThat(requests.findById(fixture.request().getAsRequestId()).orElseThrow().getAsStatus()).isEqualTo(next);
		}
	}

	@Test
	void rejectsCommentOnCompletedRequestWithoutSaving() throws Exception {
		var fixture = fixture(AsStatus.COMPLETED);
		mvc.perform(csrfWrite(path(fixture, "comments"), "comments", true)
			.header("Authorization", "Bearer " + token(fixture.owner())).content("{\"content\":\"추가 조치\"}"))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AS_ALREADY_COMPLETED"));
		assertThat(comments.findAllByAsRequest_AsRequestIdOrderByCreatedAtAscAsCommentIdAsc(fixture.request().getAsRequestId())).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(value = AsStatus.class, names = {"RECEIVED", "CHECKING", "IN_PROGRESS"})
	void rejectsReopeningCompletedRequest(AsStatus target) throws Exception {
		var fixture = fixture(AsStatus.COMPLETED);
		mvc.perform(csrfWrite(path(fixture, "status"), "status", true)
			.header("Authorization", "Bearer " + token(fixture.owner()))
			.content(mapper.writeValueAsString(Map.of("asStatus", target.name()))))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AS_ALREADY_COMPLETED"));
		assertThat(requests.findById(fixture.request().getAsRequestId()).orElseThrow().getAsStatus()).isEqualTo(AsStatus.COMPLETED);
	}

	@Test
	void repeatsCompletedStatusWithoutUpdatingTimestampAndStillReadsIt() throws Exception {
		var fixture = fixture(AsStatus.COMPLETED);
		comment(fixture.request(), fixture.owner().user(), "완료 이전 조치 기록");
		var fixed = LocalDateTime.of(2026, 11, 1, 10, 0);
		jdbc.update("update as_request set updated_at = ? where as_request_id = ?", fixed, fixture.request().getAsRequestId());
		var token = token(fixture.owner());
		mvc.perform(csrfWrite(path(fixture, "status"), "status", true).header("Authorization", "Bearer " + token)
			.content("{\"asStatus\":\"COMPLETED\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.asStatus").value("COMPLETED"));
		assertThat(requests.findById(fixture.request().getAsRequestId()).orElseThrow().getUpdatedAt()).isEqualTo(fixed);
		mvc.perform(get("/as-requests/" + fixture.request().getAsRequestId()).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk()).andExpect(jsonPath("$.comments.length()").value(1));
		mvc.perform(get("/as-requests/received").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk()).andExpect(jsonPath("$.requests[0].asStatus").value("COMPLETED"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"comments", "status"})
	void rejectsForeignTenantAndMissingRequestsWithSameNotFound(String operation) throws Exception {
		var fixture = fixture(AsStatus.RECEIVED);
		for (Account intruder : List.of(fixture.tenant(), account(Role.USER, "타인"))) {
			var token = token(intruder);
			var foreign = mvc.perform(csrfWrite(path(fixture, operation), operation, true)
				.param("landlordId", fixture.owner().user().getUserId().toString())
				.header("Authorization", "Bearer " + token).content(body(operation)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("AS_REQUEST_NOT_FOUND")).andReturn();
			var missing = mvc.perform(csrfWrite("/as-requests/" + Long.MAX_VALUE + "/" + operation, operation, true)
				.header("Authorization", "Bearer " + token).content(body(operation)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("AS_REQUEST_NOT_FOUND")).andReturn();
			assertThat(foreign.getResponse().getContentAsString()).isEqualTo(missing.getResponse().getContentAsString());
		}
		assertThat(comments.findAllByAsRequest_AsRequestIdOrderByCreatedAtAscAsCommentIdAsc(fixture.request().getAsRequestId())).isEmpty();
		assertThat(requests.findById(fixture.request().getAsRequestId()).orElseThrow().getAsStatus()).isEqualTo(AsStatus.RECEIVED);
	}

	@ParameterizedTest
	@ValueSource(strings = {"comments", "status"})
	void requiresJwtUserRoleAndRealCsrf(String operation) throws Exception {
		var fixture = fixture(AsStatus.RECEIVED);
		String path = path(fixture, operation);
		mvc.perform(csrfWrite(path, operation, true).content(body(operation))).andExpect(status().isUnauthorized());
		mvc.perform(csrfWrite(path, operation, true).header("Authorization", "Bearer invalid").content(body(operation)))
			.andExpect(status().isUnauthorized());
		for (Role role : List.of(Role.AGENT, Role.ADMIN)) {
			mvc.perform(csrfWrite(path, operation, true).header("Authorization", "Bearer " + token(account(role, role.name())))
				.content(body(operation))).andExpect(status().isForbidden());
		}
		var ownerToken = token(fixture.owner());
		mvc.perform(write(path, operation).header("Authorization", "Bearer " + ownerToken).content(body(operation)))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		mvc.perform(csrfWrite(path, operation, false).header("Authorization", "Bearer " + ownerToken).content(body(operation)))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	@Test
	void enforcesCommentLengthAndRequiredContentInRealRequests() throws Exception {
		var fixture = fixture(AsStatus.RECEIVED);
		var token = token(fixture.owner());
		for (String invalid : List.of("{}", "{\"content\":null}", "{\"content\":\"\"}", "{\"content\":\"  \"}",
				mapper.writeValueAsString(Map.of("content", "가".repeat(1001))))) {
			mvc.perform(csrfWrite(path(fixture, "comments"), "comments", true).header("Authorization", "Bearer " + token).content(invalid))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
		mvc.perform(csrfWrite(path(fixture, "comments"), "comments", true).header("Authorization", "Bearer " + token)
			.content(mapper.writeValueAsString(Map.of("content", "가".repeat(1000)))))
			.andExpect(status().isCreated());
		assertThat(comments.findAllByAsRequest_AsRequestIdOrderByCreatedAtAscAsCommentIdAsc(fixture.request().getAsRequestId()))
			.hasSize(1).first().extracting(AfterSalesComment::getContent).isEqualTo("가".repeat(1000));
	}

	@Test
	void rejectsMissingAndUnknownStatusWithoutChangingData() throws Exception {
		var fixture = fixture(AsStatus.RECEIVED);
		var token = token(fixture.owner());
		for (String invalid : List.of("{}", "{\"asStatus\":null}", "{\"asStatus\":\"WRONG\"}", "{\"asStatus\":0}")) {
			mvc.perform(csrfWrite(path(fixture, "status"), "status", true).header("Authorization", "Bearer " + token).content(invalid))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
		assertThat(requests.findById(fixture.request().getAsRequestId()).orElseThrow().getAsStatus()).isEqualTo(AsStatus.RECEIVED);
	}

	@ParameterizedTest
	@ValueSource(strings = {"comments", "status"})
	void serializesWritesAgainstCompletion(String operation) throws Exception {
		var fixture = fixture(AsStatus.RECEIVED);
		var locked = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var writerStarted = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var completion = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				var request = requests.findForUpdateByAsRequestIdAndProperty_Landlord_UserId(
					fixture.request().getAsRequestId(), fixture.owner().user().getUserId()).orElseThrow();
				request.changeStatus(AsStatus.COMPLETED);
				locked.countDown();
				try {
					if (!release.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("완료 트랜잭션 대기 시간 초과"); }
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(exception);
				}
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
			var blocked = executor.submit(() -> {
				writerStarted.countDown();
				if (operation.equals("comments")) {
					service.createAfterSalesComment(fixture.owner().user().getUserId(), fixture.request().getAsRequestId(),
						new AfterSalesDto.CommentCreateRequest("완료와 겹친 댓글"));
				} else {
					service.updateAfterSalesStatus(fixture.owner().user().getUserId(), fixture.request().getAsRequestId(),
						new AfterSalesDto.StatusUpdateRequest("CHECKING"));
				}
			});
			assertThat(writerStarted.await(10, TimeUnit.SECONDS)).isTrue();
			// 완료 트랜잭션이 커밋되기 전에는 뒤따른 쓰기가 잠금을 통과하지 못해야 한다.
			assertThrows(TimeoutException.class, () -> blocked.get(200, TimeUnit.MILLISECONDS));
			release.countDown();
			completion.get(10, TimeUnit.SECONDS);
			var exception = assertThrows(ExecutionException.class, () -> blocked.get(10, TimeUnit.SECONDS));
			assertThat(exception.getCause()).isInstanceOf(AfterSalesException.class);
			assertThat(((AfterSalesException) exception.getCause()).getErrorCode()).isEqualTo(ErrorCode.AS_ALREADY_COMPLETED);
			assertThat(requests.findById(fixture.request().getAsRequestId()).orElseThrow().getAsStatus()).isEqualTo(AsStatus.COMPLETED);
			assertThat(comments.findAllByAsRequest_AsRequestIdOrderByCreatedAtAscAsCommentIdAsc(fixture.request().getAsRequestId())).isEmpty();
		} finally {
			release.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
	}

	private Fixture fixture(AsStatus status) {
		var owner = account(Role.USER, "임대인");
		var tenant = account(Role.USER, "임차인");
		var property = properties.save(Property.builder().landlord(owner.user()).leaseType(LeaseType.JEONSE)
			.deposit(BigDecimal.TEN).roadAddress("서울특별시 마포구 응답로 1").detailAddress("101호").build());
		var request = requests.save(AfterSalesRequest.builder().property(property).tenant(tenant.user())
			.content("누수 점검").asStatus(status).build());
		return new Fixture(owner, tenant, property, request);
	}

	private Account account(Role role, String name) {
		var password = UUID.randomUUID().toString();
		var user = users.save(User.builder().email(UUID.randomUUID() + "@example.com").passwordHash(encoder.encode(password))
			.name(name).phone("01000000000").role(role).build());
		return new Account(user, password);
	}

	private AfterSalesComment comment(AfterSalesRequest request, User author, String content) {
		return comments.save(AfterSalesComment.builder().asRequest(request).author(author).content(content).build());
	}

	private String token(Account account) {
		return auth.login(account.user().getEmail(), account.password()).getAccessToken();
	}

	private String path(Fixture fixture, String operation) {
		return "/as-requests/" + fixture.request().getAsRequestId() + "/" + operation;
	}

	private String body(String operation) {
		return operation.equals("comments") ? "{\"content\":\"조치 사항\"}" : "{\"asStatus\":\"CHECKING\"}";
	}

	private MockHttpServletRequestBuilder write(String path, String operation) {
		return (operation.equals("comments") ? post(path) : patch(path)).contentType(MediaType.APPLICATION_JSON);
	}

	private MockHttpServletRequestBuilder csrfWrite(String path, String operation, boolean valid) throws Exception {
		// 실제 발급받은 쿠키·마스킹 헤더를 사용해 CSRF 필터를 우회하지 않는다.
		var response = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
		var csrf = mapper.readTree(response.getContentAsString());
		return write(path, operation).cookie(response.getCookie("BOGEUM_CSRF"))
			.header(csrf.get("headerName").asText(), valid ? csrf.get("token").asText() : "invalid");
	}

	private record Account(User user, String password) { }
	private record Fixture(Account owner, Account tenant, Property property, AfterSalesRequest request) { }
}
