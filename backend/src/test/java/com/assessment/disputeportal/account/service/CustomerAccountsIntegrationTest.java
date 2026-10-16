package com.assessment.disputeportal.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import com.assessment.disputeportal.account.cli.AccountFixtureException;
import com.assessment.disputeportal.account.cli.AccountFixturePort;
import com.assessment.disputeportal.account.cli.AccountFixtureService;
import com.assessment.disputeportal.account.model.AccountType;
import com.assessment.disputeportal.account.repository.AccountReadRepository;
import com.assessment.disputeportal.security.authentication.VersionedPasswordEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
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
class CustomerAccountsIntegrationTest {
  private static final String PASSWORD = "account integration exact password";
  private static final Instant MAIN_CREATED_AT = Instant.parse("2025-01-01T00:00:00Z");
  private static final Instant SAVINGS_CREATED_AT = Instant.parse("2025-01-02T00:00:00Z");

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
  @Autowired AccountFixtureService fixtures;
  @Autowired AccountFixturePort fixturePort;
  @MockitoSpyBean AccountReadRepository accounts;

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @BeforeEach
  void clearDatabase() {
    jdbc.update("DELETE FROM spring_session");
    jdbc.update("DELETE FROM accounts");
    jdbc.update("DELETE FROM app_users");
  }

  @Test
  void v5CheckRejectsMalformedNumbersAndPartialIndexRejectsSecondPrimary() {
    UUID customer = customer("schema@example.test", "CUSTOMER", true);
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                insertAccount(
                    first,
                    customer,
                    "123456789",
                    "MAIN",
                    true,
                    new BigDecimal("12.00"),
                    MAIN_CREATED_AT))
        .isInstanceOf(DataAccessException.class);

    insertAccount(
        first, customer, "0123456789", "MAIN", true, new BigDecimal("12.00"), MAIN_CREATED_AT);
    assertThatThrownBy(
            () ->
                insertAccount(
                    second,
                    customer,
                    "9876543210",
                    "SAVINGS",
                    true,
                    new BigDecimal("34.00"),
                    SAVINGS_CREATED_AT))
        .isInstanceOf(DataAccessException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE customer_id = ?", Integer.class, customer))
        .isEqualTo(1);
  }

  @Test
  void fixtureCreatesDistinctAccountsForMultipleCustomersWithStableIdsAndTimestamps() {
    UUID customerA = customer("a@example.test", "CUSTOMER", true);
    UUID customerB = customer("b@example.test", "CUSTOMER", true);
    int usersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class);

    Map<String, UUID> accountsA = fixtures.createFixtureAccounts("a@example.test");
    Map<String, UUID> accountsB = fixtures.createFixtureAccounts("b@example.test");

    assertThat(new HashSet<>(accountsA.values())).hasSize(2);
    assertThat(new HashSet<>(accountsB.values())).hasSize(2);
    assertThat(
            new HashSet<>(
                List.of(
                    accountsA.get("MAIN"),
                    accountsA.get("SAVINGS"),
                    accountsB.get("MAIN"),
                    accountsB.get("SAVINGS"))))
        .hasSize(4);
    assertThat(fixtureNumbers()).hasSize(4);
    assertThat(fixtures.getFixtureAccountIds("a@example.test")).isEqualTo(accountsA);
    assertThat(fixturePort.getFixtureAccountIds("b@example.test")).isEqualTo(accountsB);
    assertThat(accountCount()).isEqualTo(4);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class))
        .isEqualTo(usersBefore);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE customer_id = ? AND account_type = 'MAIN' AND is_primary",
                Integer.class,
                customerA))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE customer_id = ? AND account_type = 'SAVINGS' AND NOT is_primary",
                Integer.class,
                customerB))
        .isEqualTo(1);
    assertThat(createdAt(accountsA.get("MAIN"))).isEqualTo(MAIN_CREATED_AT);
    assertThat(createdAt(accountsA.get("SAVINGS"))).isEqualTo(SAVINGS_CREATED_AT);

    assertThat(fixtures.createFixtureAccounts("a@example.test")).isEqualTo(accountsA);
    assertThat(fixtures.createFixtureAccounts("b@example.test")).isEqualTo(accountsB);
    assertThat(accountCount()).isEqualTo(4);
  }

  @Test
  void fixtureResolvesAccountNumberCandidateCollisionDeterministically() {
    UUID target = customer("target@example.test", "CUSTOMER", true);
    customer("occupant@example.test", "CUSTOMER", true);
    String collidingNumber = AccountFixtureService.syntheticNumber(target, AccountType.MAIN, 0);
    UUID occupantAccount = UUID.randomUUID();
    UUID occupant = userId("occupant@example.test");
    insertAccount(
        occupantAccount,
        occupant,
        collidingNumber,
        "MAIN",
        true,
        new BigDecimal("10.00"),
        MAIN_CREATED_AT);

    Map<String, UUID> ids = fixtures.createFixtureAccounts("target@example.test");
    String targetMainNumber =
        jdbc.queryForObject(
            "SELECT account_number FROM accounts WHERE id = ?", String.class, ids.get("MAIN"));
    assertThat(targetMainNumber)
        .isEqualTo(AccountFixtureService.syntheticNumber(target, AccountType.MAIN, 1));
    assertThat(targetMainNumber).isNotEqualTo(collidingNumber);
    assertThat(fixtures.createFixtureAccounts("target@example.test")).isEqualTo(ids);
    assertThat(
            jdbc.queryForObject(
                "SELECT account_number FROM accounts WHERE id = ?", String.class, ids.get("MAIN")))
        .isEqualTo(targetMainNumber);
    assertThat(fixtureNumbers()).hasSize(3);
  }

  @Test
  void fixtureRefusesDisabledAgentAndMissingTargetsWithoutCreatingUsersOrRows() {
    customer("disabled@example.test", "CUSTOMER", false);
    customer("agent@example.test", "AGENT", true);
    int usersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class);

    for (String email :
        List.of("disabled@example.test", "agent@example.test", "missing@example.test")) {
      assertThatThrownBy(() -> fixtures.createFixtureAccounts(email))
          .isInstanceOf(AccountFixtureException.class)
          .hasMessage("An enabled CUSTOMER account is required.");
    }
    assertThat(accountCount()).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class))
        .isEqualTo(usersBefore);
  }

  @Test
  void fixtureMismatchRollsBackAndDoesNotReseedExistingAccounts() {
    UUID customer = customer("mismatch@example.test", "CUSTOMER", true);
    Map<String, UUID> ids = fixtures.createFixtureAccounts("mismatch@example.test");
    jdbc.update("UPDATE accounts SET balance = 999.00 WHERE id = ?", ids.get("MAIN"));

    assertThatThrownBy(() -> fixtures.createFixtureAccounts("mismatch@example.test"))
        .isInstanceOf(AccountFixtureException.class)
        .hasMessage("Existing fixture data does not match the expected account state.");
    assertThat(accountCount()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, ids.get("MAIN")))
        .isEqualByComparingTo("999.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE id = ?", Integer.class, customer))
        .isEqualTo(1);
  }

  @Test
  void customerHttpResponseIsOwnerScopedAndNeverContainsAnotherCustomersNumber() throws Exception {
    UUID customerA = customer("a@example.test", "CUSTOMER", true);
    UUID customerB = customer("b@example.test", "CUSTOMER", true);
    Map<String, UUID> fixtureA = fixtures.createFixtureAccounts("a@example.test");
    fixtures.createFixtureAccounts("b@example.test");
    List<String> customerBNumbers =
        jdbc.query(
            "SELECT account_number FROM accounts WHERE customer_id = ?",
            (row, index) -> row.getString(1),
            customerB);
    List<String> customerANumbers =
        jdbc.query(
            "SELECT account_number FROM accounts WHERE customer_id = ?",
            (row, index) -> row.getString(1),
            customerA);

    String cookie = login("a@example.test");
    HttpResponse<String> response = get("/api/v1/accounts", cookie);
    JsonNode body = json.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    assertThat(response.headers().firstValue("X-Correlation-ID")).isPresent();
    assertThat(body.path("status").asBoolean()).isTrue();
    assertThat(body.at("/data/total").asLong()).isEqualTo(2);
    assertThat(body.at("/data/items/0/id").asText()).isEqualTo(fixtureA.get("MAIN").toString());
    customerANumbers.forEach(number -> assertThat(response.body()).contains(number));
    assertThat(response.body()).doesNotContain(customerBNumbers.toArray(String[]::new));

    HttpResponse<String> secondPage = get("/api/v1/accounts?page=2&size=1", cookie);
    assertThat(secondPage.statusCode()).isEqualTo(200);
    assertThat(json.readTree(secondPage.body()).at("/data/items/0/id").asText())
        .isEqualTo(fixtureA.get("SAVINGS").toString());

    HttpResponse<String> unknown = get("/api/v1/accounts?search=0123456789", cookie);
    assertThat(unknown.statusCode()).isEqualTo(400);
    assertThat(unknown.body()).doesNotContain(customerBNumbers.toArray(String[]::new));
    HttpResponse<String> duplicate = get("/api/v1/accounts?page=1&page=2", cookie);
    assertThat(duplicate.statusCode()).isEqualTo(400);
  }

  @Test
  void emptyAccountsAndRoleBoundariesUseStandardResponses() throws Exception {
    customer("empty@example.test", "CUSTOMER", true);
    customer("agent@example.test", "AGENT", true);
    String customerCookie = login("empty@example.test");
    JsonNode empty = json.readTree(get("/api/v1/accounts", customerCookie).body());
    assertThat(empty.at("/data/total").asLong()).isZero();
    assertThat(empty.at("/data/items").isEmpty()).isTrue();

    String agentCookie = login("agent@example.test");
    HttpResponse<String> forbidden = get("/api/v1/accounts", agentCookie);
    assertThat(forbidden.statusCode()).isEqualTo(403);
    assertThat(json.readTree(forbidden.body()).path("error_code").asInt()).isEqualTo(1002);
    HttpResponse<String> anonymous = get("/api/v1/accounts", null);
    assertThat(anonymous.statusCode()).isEqualTo(401);
    assertThat(json.readTree(anonymous.body()).path("error_code").asInt()).isEqualTo(1001);
  }

  @Test
  void databaseReadFailureUsesTheStandardUnavailableEnvelope() throws Exception {
    UUID customer = customer("unavailable@example.test", "CUSTOMER", true);
    String cookie = login("unavailable@example.test");
    doThrow(new org.springframework.dao.DataAccessResourceFailureException("private test fault"))
        .when(accounts)
        .countOwnedBy(customer);

    HttpResponse<String> response = get("/api/v1/accounts", cookie);
    assertThat(response.statusCode()).isEqualTo(503);
    assertThat(json.readTree(response.body()).path("error_code").asInt()).isEqualTo(3002);
    assertThat(response.body()).doesNotContain("private test fault");
  }

  private UUID customer(String email, String role, boolean enabled) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app_users(id,email,password_hash,role,enabled,created_at) VALUES(?,?,?,?,?,?)",
        id,
        email,
        passwords.encode(PASSWORD),
        role,
        enabled,
        java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
    return id;
  }

  private UUID userId(String email) {
    return jdbc.queryForObject("SELECT id FROM app_users WHERE email = ?", UUID.class, email);
  }

  private void insertAccount(
      UUID id,
      UUID customer,
      String number,
      String type,
      boolean primary,
      BigDecimal balance,
      Instant createdAt) {
    jdbc.update(
        "INSERT INTO accounts(id,customer_id,account_number,account_type,balance,is_primary,created_at) VALUES(?,?,?,?,?,?,?)",
        id,
        customer,
        number,
        type,
        balance,
        primary,
        java.sql.Timestamp.from(createdAt));
  }

  private List<String> fixtureNumbers() {
    return jdbc.query(
        "SELECT account_number FROM accounts ORDER BY account_number",
        (row, index) -> row.getString(1));
  }

  private int accountCount() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class);
  }

  private Instant createdAt(UUID accountId) {
    return jdbc.queryForObject(
        "SELECT created_at FROM accounts WHERE id = ?",
        (row, index) -> row.getObject(1, java.time.OffsetDateTime.class).toInstant(),
        accountId);
  }

  private String login(String email) throws Exception {
    HttpResponse<String> csrf = get("/api/v1/auth/csrf", null);
    String preAuthCookie = cookie(csrf);
    String token = json.readTree(csrf.body()).at("/data/csrfToken").asText();
    String payload = json.writeValueAsString(Map.of("email", email, "password", PASSWORD));
    HttpRequest request =
        HttpRequest.newBuilder(uri("/api/v1/auth/login"))
            .timeout(Duration.ofSeconds(10))
            .header("Cookie", preAuthCookie)
            .header("X-CSRF-TOKEN", token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return cookie(response);
  }

  private HttpResponse<String> get(String path, String cookie) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET();
    if (cookie != null) request.header("Cookie", cookie);
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private static String cookie(HttpResponse<String> response) {
    return response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
  }
}
