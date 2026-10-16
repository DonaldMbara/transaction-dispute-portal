package com.assessment.disputeportal.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.assessment.disputeportal.account.cli.AccountFixtureService;
import com.assessment.disputeportal.dispute.repository.CustomerDisputeReadRepository;
import com.assessment.disputeportal.security.authentication.VersionedPasswordEncoder;
import com.assessment.disputeportal.transaction.cli.TransactionFixtureService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class CustomerDisputeControllerIntegrationTest {

  private static final String PASSWORD = "dispute-controller-test-password";
  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse(
                  "postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3")
              .asCompatibleSubstituteFor("postgres"));

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", postgres::getJdbcUrl);
    properties.add("spring.datasource.username", postgres::getUsername);
    properties.add("spring.datasource.password", postgres::getPassword);
  }

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired VersionedPasswordEncoder passwords;
  @Autowired AccountFixtureService accountFixtures;
  @Autowired TransactionFixtureService transactionFixtures;
  @MockitoSpyBean CustomerDisputeReadRepository repository;

  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  private UUID customerId;
  private UUID agentId;
  private UUID mainAccountId;

  @BeforeEach
  void clean() {
    reset(repository);
    jdbc.update("DELETE FROM spring_session");
    jdbc.update("DELETE FROM idempotency_records");
    jdbc.update("DELETE FROM dispute_events");
    jdbc.update("DELETE FROM disputes");
    jdbc.update("DELETE FROM bank_transactions");
    jdbc.update("DELETE FROM accounts");
    jdbc.update("DELETE FROM app_users");
  }

  private void setupUsers() {
    customerId = createUser("customer@test.com", "CUSTOMER");
    agentId = createUser("agent@test.com", "AGENT");
    accountFixtures.createFixtureAccounts("customer@test.com");
    transactionFixtures.ensureCustomerFixtures("customer@test.com");
    mainAccountId =
        jdbc.queryForObject(
            "SELECT id FROM accounts WHERE customer_id = ? AND account_type = 'MAIN'",
            UUID.class,
            customerId);
  }

  private UUID createUser(String email, String role) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app_users(id,email,password_hash,role,enabled,created_at) VALUES(?,?,?,?,true,?)",
        id,
        email,
        passwords.encode(PASSWORD),
        role,
        java.sql.Timestamp.from(CREATED_AT));
    return id;
  }

  private UUID createOpenDispute(UUID transactionId, Instant createdAt) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
                INSERT INTO disputes(id, transaction_id, reason, description, status,
                  created_at, updated_at, version)
                VALUES (?,?,?,?,?,?,?,0)
                """,
        id,
        transactionId,
        "DUPLICATE_CHARGE",
        "Was charged twice",
        "OPEN",
        java.sql.Timestamp.from(createdAt),
        java.sql.Timestamp.from(createdAt));
    jdbc.update(
        """
                INSERT INTO dispute_events(id, dispute_id, actor_id, dispute_version, event_type,
                  from_status, to_status, customer_message, created_at)
                VALUES (?,?,?,0,'CREATED',NULL,'OPEN',NULL,?)
                """,
        UUID.randomUUID(),
        id,
        customerId,
        java.sql.Timestamp.from(createdAt));
    return id;
  }

  private UUID createResolvedDispute(UUID transactionId, String status, Instant createdAt) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
                INSERT INTO disputes(id, transaction_id, reason, description, status,
                  assigned_agent_id, assigned_at, internal_resolution_notes,
                  customer_resolution_message, created_at, updated_at, resolved_at, version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,0)
                """,
        id,
        transactionId,
        "DUPLICATE_CHARGE",
        "Was charged twice",
        status,
        agentId,
        java.sql.Timestamp.from(createdAt),
        "Internal note",
        "Customer outcome message",
        java.sql.Timestamp.from(createdAt),
        java.sql.Timestamp.from(createdAt.plusSeconds(3600)),
        java.sql.Timestamp.from(createdAt.plusSeconds(3600)));
    jdbc.update(
        """
                INSERT INTO dispute_events(id, dispute_id, actor_id, dispute_version, event_type,
                  from_status, to_status, customer_message, created_at)
                VALUES (?,?,?,0,'CREATED',NULL,'OPEN',NULL,?)
                """,
        UUID.randomUUID(),
        id,
        customerId,
        java.sql.Timestamp.from(createdAt));
    return id;
  }

  private Session login(String email) throws Exception {
    HttpResponse<String> preflight = get("/api/v1/auth/csrf", null);
    String preCookie = cookie(preflight);
    String preToken = json.readTree(preflight.body()).at("/data/csrfToken").asText();
    HttpRequest login =
        HttpRequest.newBuilder(uri("/api/v1/auth/login"))
            .timeout(Duration.ofSeconds(10))
            .header("Cookie", preCookie)
            .header("X-CSRF-TOKEN", preToken)
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
            .build();
    HttpResponse<String> response = client.send(login, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as("Login should succeed for " + email).isEqualTo(200);
    String sessionCookie = cookie(response);
    HttpResponse<String> csrf = get("/api/v1/auth/csrf", sessionCookie);
    return new Session(sessionCookie, json.readTree(csrf.body()).at("/data/csrfToken").asText());
  }

  private HttpResponse<String> get(String path, String cookie) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET();
    if (cookie != null) builder.header("Cookie", cookie);
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(
      String path, Session session, String body, Map<String, String> headers) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(uri(path))
            .timeout(Duration.ofSeconds(10))
            .header("Cookie", session.cookie())
            .header("X-CSRF-TOKEN", session.token())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    headers.forEach(builder::header);
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private static String cookie(HttpResponse<String> response) {
    return response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
  }

  private record Session(String cookie, String token) {}

  // =========================================================================

  @Test
  void anonymous_returns401_1001() throws Exception {
    HttpResponse<String> response = get("/api/v1/disputes", null);
    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(json.readTree(response.body()).path("error_code").asInt()).isEqualTo(1001);
  }

  @Test
  void agentRole_returns403_1002() throws Exception {
    setupUsers();
    Session agentSession = login("agent@test.com");
    HttpResponse<String> response = get("/api/v1/disputes", agentSession.cookie());
    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(json.readTree(response.body()).path("error_code").asInt()).isEqualTo(1002);
  }

  @Test
  void ownedList_returns200WithItems() throws Exception {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);
    createOpenDispute(txIds.get(0), Instant.parse("2026-10-01T10:00:00Z"));

    Session session = login("customer@test.com");
    HttpResponse<String> response = get("/api/v1/disputes", session.cookie());
    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode data = json.readTree(response.body()).path("data");
    assertThat(data.path("total").asInt()).isEqualTo(1);
    assertThat(data.path("items").isArray()).isTrue();
    assertThat(data.path("items").size()).isEqualTo(1);
  }

  @Test
  void nonOwnedDetail_returns404_1003() throws Exception {
    setupUsers();
    // Create a dispute for another customer's transaction
    createUser("other@test.com", "CUSTOMER");
    accountFixtures.createFixtureAccounts("other@test.com");
    transactionFixtures.ensureCustomerFixtures("other@test.com");
    UUID otherAccountId =
        jdbc.queryForObject(
            "SELECT id FROM accounts WHERE customer_id = (SELECT id FROM app_users WHERE email='other@test.com') AND account_type = 'MAIN'",
            UUID.class);
    List<UUID> otherTxIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            otherAccountId);
    UUID otherDisputeId =
        createOpenDispute(otherTxIds.get(0), Instant.parse("2026-10-01T10:00:00Z"));

    Session session = login("customer@test.com");
    HttpResponse<String> response = get("/api/v1/disputes/" + otherDisputeId, session.cookie());
    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(json.readTree(response.body()).path("error_code").asInt()).isEqualTo(1003);
  }

  @Test
  void malformedUuidPath_returns400_1004() throws Exception {
    setupUsers();
    Session session = login("customer@test.com");
    HttpResponse<String> response = get("/api/v1/disputes/not-a-valid-uuid", session.cookie());
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(json.readTree(response.body()).path("error_code").asInt()).isEqualTo(1004);
  }

  @Test
  void accountIdOnDetailEndpoint_returns400_1004() throws Exception {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);
    UUID disputeId = createOpenDispute(txIds.get(0), Instant.parse("2026-10-01T10:00:00Z"));

    Session session = login("customer@test.com");
    HttpResponse<String> response =
        get("/api/v1/disputes/" + disputeId + "?accountId=" + mainAccountId, session.cookie());
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(json.readTree(response.body()).path("error_code").asInt()).isEqualTo(1004);
  }

  @Test
  void unknownQueryParamOnList_returns400_1004() throws Exception {
    setupUsers();
    Session session = login("customer@test.com");
    HttpResponse<String> response = get("/api/v1/disputes?unknownParam=value", session.cookie());
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(json.readTree(response.body()).path("error_code").asInt()).isEqualTo(1004);
  }

  @Test
  void nonOwnedAccountIdOnList_returns404_1003() throws Exception {
    setupUsers();
    // Create another customer with accounts
    createUser("other2@test.com", "CUSTOMER");
    accountFixtures.createFixtureAccounts("other2@test.com");
    UUID otherAccId =
        jdbc.queryForObject(
            "SELECT id FROM accounts WHERE customer_id = (SELECT id FROM app_users WHERE email='other2@test.com') AND account_type = 'MAIN'",
            UUID.class);

    Session session = login("customer@test.com");
    // Try to query with an account that belongs to another customer
    HttpResponse<String> response =
        get("/api/v1/disputes?accountId=" + otherAccId, session.cookie());
    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(json.readTree(response.body()).path("error_code").asInt()).isEqualTo(1003);
  }

  @Test
  void statusActive_returns200WithOnlyOpenAndUnderReview() throws Exception {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);

    createOpenDispute(txIds.get(0), Instant.parse("2026-10-01T10:00:00Z")); // OPEN
    createResolvedDispute(
        txIds.get(1), "ACCEPTED", Instant.parse("2026-10-03T10:00:00Z")); // ACCEPTED

    Session session = login("customer@test.com");
    HttpResponse<String> response = get("/api/v1/disputes?status=ACTIVE", session.cookie());
    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode items = json.readTree(response.body()).path("data").path("items");
    assertThat(items.size()).isEqualTo(1);
    assertThat(items.get(0).path("status").asText()).isEqualTo("OPEN");
  }

  @Test
  void statusResolved_returns200WithOnlyAcceptedAndRejected() throws Exception {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);

    createOpenDispute(txIds.get(0), Instant.parse("2026-10-01T10:00:00Z")); // OPEN
    createResolvedDispute(
        txIds.get(1), "ACCEPTED", Instant.parse("2026-10-03T10:00:00Z")); // ACCEPTED
    createResolvedDispute(
        txIds.get(2), "REJECTED", Instant.parse("2026-10-05T10:00:00Z")); // REJECTED

    Session session = login("customer@test.com");
    HttpResponse<String> response = get("/api/v1/disputes?status=RESOLVED", session.cookie());
    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode items = json.readTree(response.body()).path("data").path("items");
    assertThat(items.size()).isEqualTo(2);
    for (JsonNode item : items) {
      assertThat(item.path("status").asText()).isIn("ACCEPTED", "REJECTED");
    }
  }

  @Test
  void statusBadValue_returns400_1004() throws Exception {
    setupUsers();
    Session session = login("customer@test.com");
    HttpResponse<String> response = get("/api/v1/disputes?status=BADVALUE", session.cookie());
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(json.readTree(response.body()).path("error_code").asInt()).isEqualTo(1004);
  }

  @Test
  void dbFailure_returns503_3002() throws Exception {
    setupUsers();
    doThrow(new DataAccessResourceFailureException("Simulated DB failure"))
        .when(repository)
        .findOwned(any());

    Session session = login("customer@test.com");
    HttpResponse<String> response = get("/api/v1/disputes", session.cookie());
    assertThat(response.statusCode()).isEqualTo(503);
    assertThat(json.readTree(response.body()).path("error_code").asInt()).isEqualTo(3002);
  }

  @Test
  void serializedBody_doesNotContainProhibitedKeys() throws Exception {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);
    UUID disputeId = createOpenDispute(txIds.get(0), Instant.parse("2026-10-01T10:00:00Z"));

    Session session = login("customer@test.com");

    // Test list response
    HttpResponse<String> listResponse = get("/api/v1/disputes", session.cookie());
    assertThat(listResponse.statusCode()).isEqualTo(200);
    String listBody = listResponse.body();
    assertProhibitedKeysAbsent(listBody);

    // Test detail response
    HttpResponse<String> detailResponse = get("/api/v1/disputes/" + disputeId, session.cookie());
    assertThat(detailResponse.statusCode()).isEqualTo(200);
    String detailBody = detailResponse.body();
    assertProhibitedKeysAbsent(detailBody);
  }

  @Test
  void accountContext_hasDisplayNameAndMaskedNumberNotAccountNumber() throws Exception {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);
    UUID disputeId = createOpenDispute(txIds.get(0), Instant.parse("2026-10-01T10:00:00Z"));

    Session session = login("customer@test.com");

    // Check list response
    HttpResponse<String> listResponse = get("/api/v1/disputes", session.cookie());
    JsonNode listItem = json.readTree(listResponse.body()).path("data").path("items").get(0);
    JsonNode account = listItem.path("transaction").path("account");
    assertThat(account.has("id")).isTrue();
    assertThat(account.has("displayName")).isTrue();
    assertThat(account.has("maskedNumber")).isTrue();
    assertThat(account.has("accountNumber")).isFalse();
    assertThat(account.path("displayName").asText()).isIn("Main Account", "Savings Account");

    // Check detail response
    HttpResponse<String> detailResponse = get("/api/v1/disputes/" + disputeId, session.cookie());
    JsonNode detailAccount =
        json.readTree(detailResponse.body()).path("data").path("transaction").path("account");
    assertThat(detailAccount.has("displayName")).isTrue();
    assertThat(detailAccount.has("maskedNumber")).isTrue();
    assertThat(detailAccount.has("accountNumber")).isFalse();
  }

  @Test
  void authenticatedActivity_renewsSessionForCustomer() throws Exception {
    setupUsers();
    Session session = login("customer@test.com");

    // Hit the endpoint twice; the second call should succeed (session renewed on first)
    get("/api/v1/disputes", session.cookie());
    HttpResponse<String> second = get("/api/v1/disputes", session.cookie());
    assertThat(second.statusCode()).isEqualTo(200);
  }

  @Test
  void agentQueueAndDetailExposeOnlyMaskedContextWithStableVersionTag() throws Exception {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);
    UUID disputeId = createOpenDispute(txIds.get(0), Instant.parse("2026-10-01T10:00:00Z"));
    Session session = login("agent@test.com");

    HttpResponse<String> queue = get("/api/v1/agent/disputes?queue=unassigned", session.cookie());
    assertThat(queue.statusCode()).isEqualTo(200);
    JsonNode item = json.readTree(queue.body()).at("/data/items/0");
    assertThat(json.readTree(queue.body()).at("/data/total").asInt()).isEqualTo(1);
    assertThat(item.path("status").asText()).isEqualTo("OPEN");
    assertThat(item.path("amount").asText()).matches("\\d+\\.\\d{2}");

    HttpResponse<String> detail = get("/api/v1/agent/disputes/" + disputeId, session.cookie());
    assertThat(detail.statusCode()).isEqualTo(200);
    assertThat(detail.headers().firstValue("ETag")).contains("\"dispute-" + disputeId + "-v0\"");
    assertThat(detail.body()).contains("maskedAccountNumber").doesNotContain("accountNumber");
  }

  @Test
  void claimCommitsVersionedAuditEventAndReturnsFreshEtag() throws Exception {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);
    UUID disputeId = createOpenDispute(txIds.get(0), Instant.parse("2026-10-01T10:00:00Z"));
    Session session = login("agent@test.com");
    HttpResponse<String> initial = get("/api/v1/agent/disputes/" + disputeId, session.cookie());

    HttpResponse<String> claimed =
        post(
            "/api/v1/agent/disputes/" + disputeId + "/claim",
            session,
            "{}",
            Map.of("If-Match", initial.headers().firstValue("ETag").orElseThrow()));
    assertThat(claimed.statusCode()).isEqualTo(200);
    assertThat(json.readTree(claimed.body()).at("/data/status").asText()).isEqualTo("UNDER_REVIEW");
    assertThat(json.readTree(claimed.body()).at("/data/version").asLong()).isEqualTo(1L);
    assertThat(claimed.headers().firstValue("ETag")).contains("\"dispute-" + disputeId + "-v1\"");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dispute_events WHERE dispute_id=?", Integer.class, disputeId))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT event_type FROM dispute_events WHERE dispute_id=? AND dispute_version=1",
                String.class,
                disputeId))
        .isEqualTo("CLAIMED");
  }

  private void assertProhibitedKeysAbsent(String body) {
    List<String> prohibited =
        List.of(
            "internalResolutionNotes",
            "assignedAgent",
            "assignedAt",
            "internalNotes",
            "actorId",
            "previousAgentId",
            "newAgentId",
            "accountNumber");
    for (String key : prohibited) {
      assertThat(body)
          .as("Response body should not contain key: " + key)
          .doesNotContain("\"" + key + "\"");
    }
  }
}
