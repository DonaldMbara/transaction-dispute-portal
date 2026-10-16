package com.assessment.disputeportal.dispute.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assessment.disputeportal.account.cli.AccountFixtureService;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.common.exception.ResourceNotFoundException;
import com.assessment.disputeportal.dispute.model.dto.CustomerDisputeDetailResponse;
import com.assessment.disputeportal.dispute.model.dto.CustomerDisputeListItemResponse;
import com.assessment.disputeportal.dispute.model.projection.EventProjection;
import com.assessment.disputeportal.dispute.model.query.DisputeQuery;
import com.assessment.disputeportal.dispute.repository.CustomerDisputeReadRepository;
import com.assessment.disputeportal.security.authentication.VersionedPasswordEncoder;
import com.assessment.disputeportal.transaction.cli.TransactionFixtureService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL integration tests for {@link JdbcCustomerDisputeReadRepository}. Uses Testcontainers
 * (not H2). Covers all scenarios in task 2.4.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class CustomerDisputeReadRepositoryIntegrationTest {

  private static final String PASSWORD = "repo-integration-password";
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

  @Autowired JdbcTemplate jdbc;
  @Autowired VersionedPasswordEncoder passwords;
  @Autowired AccountFixtureService accountFixtures;
  @Autowired TransactionFixtureService transactionFixtures;
  @Autowired CustomerDisputeReadRepository repository;
  @Autowired DisputeQueryParser parser;

  private UUID customerId;
  private UUID otherCustomerId;
  private UUID agentId;
  private UUID mainAccountId;
  private UUID otherAccountId;

  @BeforeEach
  void clean() {
    jdbc.update("DELETE FROM spring_session");
    jdbc.update("DELETE FROM idempotency_records");
    jdbc.update("DELETE FROM dispute_events");
    jdbc.update("DELETE FROM disputes");
    jdbc.update("DELETE FROM bank_transactions");
    jdbc.update("DELETE FROM accounts");
    jdbc.update("DELETE FROM app_users");
  }

  // -------------------------------------------------------------------------
  // Setup helpers
  // -------------------------------------------------------------------------

  private void setupUsers() {
    customerId = createUser("customer@test.com", "CUSTOMER");
    otherCustomerId = createUser("other@test.com", "CUSTOMER");
    agentId = createUser("agent@test.com", "AGENT");

    accountFixtures.createFixtureAccounts("customer@test.com");
    accountFixtures.createFixtureAccounts("other@test.com");
    transactionFixtures.ensureCustomerFixtures("customer@test.com");
    transactionFixtures.ensureCustomerFixtures("other@test.com");

    mainAccountId =
        jdbc.queryForObject(
            "SELECT id FROM accounts WHERE customer_id = ? AND account_type = 'MAIN'",
            UUID.class,
            customerId);
    otherAccountId =
        jdbc.queryForObject(
            "SELECT id FROM accounts WHERE customer_id = ? AND account_type = 'MAIN'",
            UUID.class,
            otherCustomerId);
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

  /** Creates a dispute and its CREATED event; optionally resolves it. */
  private UUID createDispute(
      UUID transactionId,
      String reason,
      String status,
      String customerMessage,
      Instant createdAt,
      Instant resolvedAt) {
    UUID disputeId = UUID.randomUUID();
    boolean resolved = "ACCEPTED".equals(status) || "REJECTED".equals(status);
    if (resolved) {
      jdbc.update(
          """
                    INSERT INTO disputes(id, transaction_id, reason, description, status,
                      assigned_agent_id, assigned_at, internal_resolution_notes,
                      customer_resolution_message, created_at, updated_at, resolved_at, version)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,0)
                    """,
          disputeId,
          transactionId,
          reason,
          "Test description",
          status,
          agentId,
          java.sql.Timestamp.from(createdAt),
          "Internal note here",
          customerMessage,
          java.sql.Timestamp.from(createdAt),
          java.sql.Timestamp.from(resolvedAt != null ? resolvedAt : createdAt),
          java.sql.Timestamp.from(resolvedAt != null ? resolvedAt : createdAt));
    } else {
      jdbc.update(
          """
                    INSERT INTO disputes(id, transaction_id, reason, description, status,
                      created_at, updated_at, version)
                    VALUES (?,?,?,?,?,?,?,0)
                    """,
          disputeId,
          transactionId,
          reason,
          "Test description",
          status,
          java.sql.Timestamp.from(createdAt),
          java.sql.Timestamp.from(createdAt));
    }

    UUID eventId = UUID.randomUUID();
    jdbc.update(
        """
                INSERT INTO dispute_events(id, dispute_id, actor_id, dispute_version, event_type,
                  from_status, to_status, customer_message, created_at)
                VALUES (?,?,?,0,'CREATED',NULL,'OPEN',NULL,?)
                """,
        eventId,
        disputeId,
        customerId,
        java.sql.Timestamp.from(createdAt));

    return disputeId;
  }

  private UUID getCompletedOutgoingTransaction(UUID accountId) {
    return jdbc.queryForObject(
        "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT' LIMIT 1",
        UUID.class,
        accountId);
  }

  private DisputeQuery buildQuery(UUID customerId, Map<String, String[]> params) {
    return parser.parse(params, customerId);
  }

  // =========================================================================
  // Test: status=ACTIVE returns OPEN+UNDER_REVIEW, excludes ACCEPTED/REJECTED
  // =========================================================================

  @Test
  void statusActive_returnsOnlyOpenAndUnderReview() {
    setupUsers();
    UUID txOpen = getCompletedOutgoingTransaction(mainAccountId);

    // Need second and third completed OUT transactions
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);

    UUID txUnderReview = txIds.get(1);
    UUID txAccepted = txIds.get(2);
    UUID txRejected = txIds.get(3);

    createDispute(txOpen, "OTHER", "OPEN", null, Instant.parse("2026-10-01T10:00:00Z"), null);
    // UNDER_REVIEW requires assignment
    UUID underReviewId = UUID.randomUUID();
    jdbc.update(
        """
                INSERT INTO disputes(id, transaction_id, reason, description, status,
                  assigned_agent_id, assigned_at, created_at, updated_at, version)
                VALUES (?,?,?,?,?,?,?,?,?,0)
                """,
        underReviewId,
        txUnderReview,
        "OTHER",
        "desc",
        "UNDER_REVIEW",
        agentId,
        java.sql.Timestamp.from(Instant.parse("2026-10-02T10:00:00Z")),
        java.sql.Timestamp.from(Instant.parse("2026-10-02T10:00:00Z")),
        java.sql.Timestamp.from(Instant.parse("2026-10-02T10:00:00Z")));
    jdbc.update(
        """
                INSERT INTO dispute_events(id, dispute_id, actor_id, dispute_version, event_type,
                  from_status, to_status, customer_message, created_at)
                VALUES (?,?,?,0,'CREATED',NULL,'OPEN',NULL,?)
                """,
        UUID.randomUUID(),
        underReviewId,
        customerId,
        java.sql.Timestamp.from(Instant.parse("2026-10-02T10:00:00Z")));

    createDispute(
        txAccepted,
        "OTHER",
        "ACCEPTED",
        "Accepted msg",
        Instant.parse("2026-10-03T10:00:00Z"),
        Instant.parse("2026-10-04T10:00:00Z"));
    createDispute(
        txRejected,
        "OTHER",
        "REJECTED",
        "Rejected msg",
        Instant.parse("2026-10-05T10:00:00Z"),
        Instant.parse("2026-10-06T10:00:00Z"));

    DisputeQuery query = buildQuery(customerId, Map.of("status", new String[] {"ACTIVE"}));
    Page<CustomerDisputeListItemResponse> result = repository.findOwned(query);

    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getContent())
        .extracting(CustomerDisputeListItemResponse::status)
        .containsExactlyInAnyOrder("OPEN", "UNDER_REVIEW");
  }

  // =========================================================================
  // Test: status=RESOLVED returns ACCEPTED+REJECTED, excludes OPEN/UNDER_REVIEW
  // =========================================================================

  @Test
  void statusResolved_returnsOnlyAcceptedAndRejected() {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);

    createDispute(txIds.get(0), "OTHER", "OPEN", null, Instant.parse("2026-10-01T10:00:00Z"), null);
    createDispute(
        txIds.get(1),
        "OTHER",
        "ACCEPTED",
        "msg",
        Instant.parse("2026-10-03T10:00:00Z"),
        Instant.parse("2026-10-04T10:00:00Z"));
    createDispute(
        txIds.get(2),
        "OTHER",
        "REJECTED",
        "msg",
        Instant.parse("2026-10-05T10:00:00Z"),
        Instant.parse("2026-10-06T10:00:00Z"));

    DisputeQuery query = buildQuery(customerId, Map.of("status", new String[] {"RESOLVED"}));
    Page<CustomerDisputeListItemResponse> result = repository.findOwned(query);

    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getContent())
        .extracting(CustomerDisputeListItemResponse::status)
        .containsExactlyInAnyOrder("ACCEPTED", "REJECTED");
  }

  // =========================================================================
  // Test: individual status values filter correctly
  // =========================================================================

  @Test
  void individualStatusValues_filterCorrectly() {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);

    createDispute(txIds.get(0), "OTHER", "OPEN", null, Instant.parse("2026-10-01T10:00:00Z"), null);
    createDispute(
        txIds.get(1),
        "OTHER",
        "ACCEPTED",
        "msg",
        Instant.parse("2026-10-03T10:00:00Z"),
        Instant.parse("2026-10-04T10:00:00Z"));
    createDispute(
        txIds.get(2),
        "OTHER",
        "REJECTED",
        "msg",
        Instant.parse("2026-10-05T10:00:00Z"),
        Instant.parse("2026-10-06T10:00:00Z"));

    for (String status : List.of("OPEN", "ACCEPTED", "REJECTED")) {
      DisputeQuery query = buildQuery(customerId, Map.of("status", new String[] {status}));
      Page<CustomerDisputeListItemResponse> result = repository.findOwned(query);
      assertThat(result.getTotalElements()).as("status=" + status).isEqualTo(1);
      assertThat(result.getContent().get(0).status()).isEqualTo(status);
    }
  }

  // =========================================================================
  // Test: unknown status value throws InvalidRequestException (400/1004)
  // =========================================================================

  @Test
  void unknownStatusValue_throwsInvalidRequestException() {
    setupUsers();
    assertThatThrownBy(
            () -> buildQuery(customerId, Map.of("status", new String[] {"BOGUS_STATUS"})))
        .isInstanceOf(InvalidRequestException.class);
  }

  // =========================================================================
  // Test: accountId selector — owned narrows; non-owned throws 404; malformed throws 400
  // =========================================================================

  @Test
  void accountId_ownedNarrows() {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);
    createDispute(txIds.get(0), "OTHER", "OPEN", null, Instant.parse("2026-10-01T10:00:00Z"), null);

    UUID savingsAccountId =
        jdbc.queryForObject(
            "SELECT id FROM accounts WHERE customer_id = ? AND account_type = 'SAVINGS'",
            UUID.class,
            customerId);

    // Query with own mainAccountId — should return 1 result
    DisputeQuery query =
        buildQuery(customerId, Map.of("accountId", new String[] {mainAccountId.toString()}));
    Page<CustomerDisputeListItemResponse> result = repository.findOwned(query);
    assertThat(result.getTotalElements()).isEqualTo(1);

    // Query with own savingsAccountId — no disputes on savings, should return 0
    DisputeQuery savingsQuery =
        buildQuery(customerId, Map.of("accountId", new String[] {savingsAccountId.toString()}));
    assertThat(repository.findOwned(savingsQuery).getTotalElements()).isZero();
  }

  @Test
  void accountId_nonOwnedThrowsResourceNotFoundException() {
    setupUsers();
    // otherAccountId belongs to another customer — should throw 404
    DisputeQuery query =
        buildQuery(customerId, Map.of("accountId", new String[] {otherAccountId.toString()}));
    // No disputes exist for this account under our customer — should throw
    // ResourceNotFoundException
    assertThatThrownBy(() -> repository.findOwned(query))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void accountId_malformedThrowsInvalidRequestException() {
    setupUsers();
    assertThatThrownBy(
            () -> buildQuery(customerId, Map.of("accountId", new String[] {"not-a-uuid"})))
        .isInstanceOf(InvalidRequestException.class);
  }

  // =========================================================================
  // Test: reference search with DSP- prefix variants and partial
  // =========================================================================

  @Test
  void referenceSearch_allVariantsMatchSameDispute() {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);

    // Use a known UUID that we can compute the DSP prefix for
    UUID knownDisputeId = UUID.fromString("5f9868e0-1272-47ee-b0d2-ef33e4e1d331");
    // Directly insert with known ID
    jdbc.update(
        """
                INSERT INTO disputes(id, transaction_id, reason, description, status,
                  created_at, updated_at, version)
                VALUES (?,?,?,?,?,?,?,0)
                """,
        knownDisputeId,
        txIds.get(0),
        "DUPLICATE_CHARGE",
        "Was charged twice",
        "OPEN",
        java.sql.Timestamp.from(Instant.parse("2026-10-01T10:00:00Z")),
        java.sql.Timestamp.from(Instant.parse("2026-10-01T10:00:00Z")));
    jdbc.update(
        """
                INSERT INTO dispute_events(id, dispute_id, actor_id, dispute_version, event_type,
                  from_status, to_status, customer_message, created_at)
                VALUES (?,?,?,0,'CREATED',NULL,'OPEN',NULL,?)
                """,
        UUID.randomUUID(),
        knownDisputeId,
        customerId,
        java.sql.Timestamp.from(Instant.parse("2026-10-01T10:00:00Z")));

    // All these should match the same dispute
    for (String search : List.of("DSP-5F9868E0", "dsp-5f9868e0", "5F9868E0", "5F98")) {
      DisputeQuery query = buildQuery(customerId, Map.of("search", new String[] {search}));
      Page<CustomerDisputeListItemResponse> result = repository.findOwned(query);
      assertThat(result.getTotalElements()).as("search=" + search).isEqualTo(1);
      assertThat(result.getContent().get(0).id()).isEqualTo(knownDisputeId);
    }

    // Non-matching reference returns empty
    DisputeQuery noMatch = buildQuery(customerId, Map.of("search", new String[] {"DSP-XXXXXXXX"}));
    Page<CustomerDisputeListItemResponse> empty = repository.findOwned(noMatch);
    assertThat(empty.getTotalElements()).isZero();
  }

  // =========================================================================
  // Test: reason filter works
  // =========================================================================

  @Test
  void reasonFilter_worksCorrectly() {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);

    createDispute(
        txIds.get(0),
        "DUPLICATE_CHARGE",
        "OPEN",
        null,
        Instant.parse("2026-10-01T10:00:00Z"),
        null);
    createDispute(txIds.get(1), "OTHER", "OPEN", null, Instant.parse("2026-10-02T10:00:00Z"), null);

    DisputeQuery query =
        buildQuery(customerId, Map.of("reason", new String[] {"DUPLICATE_CHARGE"}));
    Page<CustomerDisputeListItemResponse> result = repository.findOwned(query);

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().get(0).reason()).isEqualTo("DUPLICATE_CHARGE");
  }

  // =========================================================================
  // Test: date filter uses Johannesburg timezone boundary
  // =========================================================================

  @Test
  void dateFilter_usesJohannesburgBoundary() {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);

    // This UTC time = 2024-01-16 01:00 SAST = 2024-01-16 in Johannesburg
    Instant createdAt = Instant.parse("2024-01-15T23:00:00Z");
    createDispute(txIds.get(0), "OTHER", "OPEN", null, createdAt, null);

    // fromDate=2024-01-16 in Johannesburg should include the above dispute
    DisputeQuery includeQuery =
        buildQuery(customerId, Map.of("fromDate", new String[] {"2024-01-16"}));
    Page<CustomerDisputeListItemResponse> include = repository.findOwned(includeQuery);
    assertThat(include.getTotalElements()).isEqualTo(1);

    // fromDate=2024-01-17 in Johannesburg should exclude it
    DisputeQuery excludeQuery =
        buildQuery(customerId, Map.of("fromDate", new String[] {"2024-01-17"}));
    Page<CustomerDisputeListItemResponse> exclude = repository.findOwned(excludeQuery);
    assertThat(exclude.getTotalElements()).isZero();
  }

  // =========================================================================
  // Test: sort by createdAt ASC/DESC, updatedAt, status; UUID tie-breaker
  // =========================================================================

  @Test
  void sort_createdAtDescIsDefault() {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);

    UUID d1 =
        createDispute(
            txIds.get(0), "OTHER", "OPEN", null, Instant.parse("2026-10-01T10:00:00Z"), null);
    UUID d2 =
        createDispute(
            txIds.get(1), "OTHER", "OPEN", null, Instant.parse("2026-10-03T10:00:00Z"), null);
    UUID d3 =
        createDispute(
            txIds.get(2), "OTHER", "OPEN", null, Instant.parse("2026-10-02T10:00:00Z"), null);

    DisputeQuery query = buildQuery(customerId, Map.of()); // default sort: createdAt DESC
    Page<CustomerDisputeListItemResponse> result = repository.findOwned(query);

    List<UUID> ids = result.getContent().stream().map(CustomerDisputeListItemResponse::id).toList();
    assertThat(ids).containsExactly(d2, d3, d1);
  }

  @Test
  void sort_createdAtAsc() {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);

    UUID d1 =
        createDispute(
            txIds.get(0), "OTHER", "OPEN", null, Instant.parse("2026-10-01T10:00:00Z"), null);
    UUID d2 =
        createDispute(
            txIds.get(1), "OTHER", "OPEN", null, Instant.parse("2026-10-03T10:00:00Z"), null);

    DisputeQuery query = buildQuery(customerId, Map.of("sortDirection", new String[] {"ASC"}));
    Page<CustomerDisputeListItemResponse> result = repository.findOwned(query);

    List<UUID> ids = result.getContent().stream().map(CustomerDisputeListItemResponse::id).toList();
    assertThat(ids).containsExactly(d1, d2);
  }

  @Test
  void sort_byStatus() {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);

    createDispute(txIds.get(0), "OTHER", "OPEN", null, Instant.parse("2026-10-01T10:00:00Z"), null);
    createDispute(
        txIds.get(1),
        "OTHER",
        "ACCEPTED",
        "msg",
        Instant.parse("2026-10-03T10:00:00Z"),
        Instant.parse("2026-10-04T10:00:00Z"));

    DisputeQuery query =
        buildQuery(
            customerId,
            Map.of("sortBy", new String[] {"status"}, "sortDirection", new String[] {"ASC"}));
    Page<CustomerDisputeListItemResponse> result = repository.findOwned(query);

    // ACCEPTED < OPEN alphabetically
    assertThat(result.getContent().get(0).status()).isEqualTo("ACCEPTED");
    assertThat(result.getContent().get(1).status()).isEqualTo("OPEN");
  }

  // =========================================================================
  // Test: empty page returns total count correctly
  // =========================================================================

  @Test
  void emptyPage_returnsTotalCorrectly() {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);

    for (int i = 0; i < 3; i++) {
      createDispute(
          txIds.get(i),
          "OTHER",
          "OPEN",
          null,
          Instant.parse("2026-10-0" + (i + 1) + "T10:00:00Z"),
          null);
    }

    // Request page 999 — beyond result set
    DisputeQuery query =
        buildQuery(customerId, Map.of("page", new String[] {"999"}, "size", new String[] {"20"}));
    Page<CustomerDisputeListItemResponse> result = repository.findOwned(query);

    assertThat(result.getTotalElements()).isEqualTo(3);
    assertThat(result.getContent()).isEmpty();
  }

  // =========================================================================
  // Test: findOwnedById returns dispute for owner; empty for non-owner
  // =========================================================================

  @Test
  void findOwnedById_returnsForOwner_emptyForNonOwner() {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);

    UUID disputeId =
        createDispute(
            txIds.get(0),
            "DUPLICATE_CHARGE",
            "OPEN",
            null,
            Instant.parse("2026-10-01T10:00:00Z"),
            null);

    Optional<CustomerDisputeDetailResponse> owned = repository.findOwnedById(disputeId, customerId);
    assertThat(owned).isPresent();
    assertThat(owned.get().id()).isEqualTo(disputeId);
    assertThat(owned.get().reason()).isEqualTo("DUPLICATE_CHARGE");

    Optional<CustomerDisputeDetailResponse> nonOwned =
        repository.findOwnedById(disputeId, otherCustomerId);
    assertThat(nonOwned).isEmpty();
  }

  // =========================================================================
  // Test: findEventsByDisputeId returns events ordered by dispute_version ASC
  // =========================================================================

  @Test
  void findEventsByDisputeId_orderedByDisputeVersion() {
    setupUsers();
    List<UUID> txIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);

    UUID disputeId =
        createDispute(
            txIds.get(0), "OTHER", "OPEN", null, Instant.parse("2026-10-01T10:00:00Z"), null);

    // Add additional events at versions 1 and 2
    Instant claimedAt = Instant.parse("2026-10-02T10:00:00Z");
    Instant releasedAt = Instant.parse("2026-10-03T10:00:00Z");
    jdbc.update(
        """
                INSERT INTO dispute_events(id, dispute_id, actor_id, dispute_version, event_type,
                  from_status, to_status, customer_message, created_at)
                VALUES (?,?,?,1,'CLAIMED','OPEN','UNDER_REVIEW',NULL,?)
                """,
        UUID.randomUUID(),
        disputeId,
        agentId,
        java.sql.Timestamp.from(claimedAt));
    jdbc.update(
        """
                INSERT INTO dispute_events(id, dispute_id, actor_id, dispute_version, event_type,
                  from_status, to_status, customer_message, created_at)
                VALUES (?,?,?,2,'RELEASED','UNDER_REVIEW','OPEN',NULL,?)
                """,
        UUID.randomUUID(),
        disputeId,
        agentId,
        java.sql.Timestamp.from(releasedAt));

    List<EventProjection> events = repository.findEventsByDisputeId(disputeId);

    assertThat(events).hasSize(3);
    assertThat(events.get(0).disputeVersion()).isEqualTo(0);
    assertThat(events.get(0).eventType()).isEqualTo("CREATED");
    assertThat(events.get(1).disputeVersion()).isEqualTo(1);
    assertThat(events.get(1).eventType()).isEqualTo("CLAIMED");
    assertThat(events.get(2).disputeVersion()).isEqualTo(2);
    assertThat(events.get(2).eventType()).isEqualTo("RELEASED");
  }

  // =========================================================================
  // Test: SQL column assertion — prohibited internal columns absent from source
  // =========================================================================

  @Test
  void sqlColumnAssertion_prohibitedColumnsAbsentFromRepositorySource() throws Exception {
    // Read the source file and verify prohibited columns are not in SELECT lists
    String sourceFile =
        "src/main/java/com/assessment/disputeportal/dispute/repository/JdbcCustomerDisputeReadRepository.java";
    Path sourcePath = Path.of(System.getProperty("user.dir")).resolve(sourceFile);
    String sourceContent = Files.readString(sourcePath);

    List<String> prohibited =
        List.of(
            "assigned_agent_id",
            "assigned_at",
            "internal_resolution_notes",
            "actor_id",
            "previous_agent_id",
            "new_agent_id",
            "internal_notes");

    for (String column : prohibited) {
      // The column name should not appear in a SELECT context
      // We check it doesn't appear in any SELECT list (it may appear in WHERE if used,
      // but per spec it's not read at all)
      assertThat(sourceContent)
          .as(
              "Prohibited column '%s' should not appear in JdbcCustomerDisputeReadRepository",
              column)
          .doesNotContain(column);
    }
  }

  // =========================================================================
  // Test: ownership across two customers — scoped counts
  // =========================================================================

  @Test
  void ownershipIsolation_twoCustomers() {
    setupUsers();
    List<UUID> myTxIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            mainAccountId);
    List<UUID> otherTxIds =
        jdbc.queryForList(
            "SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='OUT'",
            UUID.class,
            otherAccountId);

    createDispute(
        myTxIds.get(0), "OTHER", "OPEN", null, Instant.parse("2026-10-01T10:00:00Z"), null);
    createDispute(
        myTxIds.get(1), "OTHER", "OPEN", null, Instant.parse("2026-10-02T10:00:00Z"), null);
    createDispute(
        otherTxIds.get(0), "OTHER", "OPEN", null, Instant.parse("2026-10-03T10:00:00Z"), null);

    DisputeQuery myQuery = buildQuery(customerId, Map.of());
    assertThat(repository.findOwned(myQuery).getTotalElements()).isEqualTo(2);

    DisputeQuery otherQuery = buildQuery(otherCustomerId, Map.of());
    assertThat(repository.findOwned(otherQuery).getTotalElements()).isEqualTo(1);
  }
}
