package com.assessment.disputeportal.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.assessment.disputeportal.account.port.AccountFixturePort;
import com.assessment.disputeportal.account.service.AccountFixtureService;
import com.assessment.disputeportal.security.authentication.VersionedPasswordEncoder;
import com.assessment.disputeportal.transaction.repository.TransactionReadRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class CustomerTransactionsIntegrationTest {
    private static final String PASSWORD = "transaction integration password";
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse(
            "postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3")
            .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource static void datasource(DynamicPropertyRegistry properties) {
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
    @Autowired AccountFixturePort fixturePort;
    @MockitoSpyBean TransactionReadRepository transactions;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach void clean() {
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM bank_transactions");
        jdbc.update("DELETE FROM accounts");
        jdbc.update("DELETE FROM app_users");
    }

    @Test
    void v6ConstraintsEnforceAmountEnumsCurrencyAndAccountForeignKey() {
        UUID customer = customer("schema@example.test", "CUSTOMER");
        UUID account = accountFixtures.createFixtureAccounts("schema@example.test").get("MAIN");
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> insertTransaction(id, UUID.randomUUID(), "missing-account", "Shop", "1.00", "IN", "COMPLETED"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> insertTransaction(id, account, "negative", "Shop", "-1.00", "IN", "COMPLETED"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> insertTransaction(id, account, "bad-direction", "Shop", "1.00", "SIDE", "COMPLETED"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> insertTransaction(id, account, "bad-status", "Shop", "1.00", "IN", "UNKNOWN"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO bank_transactions(id,account_id,reference,merchant,amount,currency,direction,status,occurred_at) VALUES(?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), account, "bad-currency", "Shop", new BigDecimal("1.00"), "USD", "IN", "COMPLETED", CREATED_AT))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        insertTransaction(id, account, "valid", "Shop", "1.00", "IN", "COMPLETED");
        assertThatThrownBy(() -> insertTransaction(UUID.randomUUID(), account, "valid", "Shop", "2.00", "OUT", "FAILED"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bank_transactions", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE id = ?", Integer.class, customer)).isEqualTo(1);
    }

    @Test
    void capturesRepresentativePostgresExplainAnalyzeBufferPlans() {
        customer("plans@example.test", "CUSTOMER");
        var accounts = accountFixtures.createFixtureAccounts("plans@example.test");
        transactionFixtures.ensureCustomerFixtures("plans@example.test");
        UUID owner = jdbc.queryForObject("SELECT customer_id FROM accounts WHERE id=?", UUID.class, accounts.get("MAIN"));
        jdbc.execute("ANALYZE accounts");
        jdbc.execute("ANALYZE bank_transactions");
        String base = "SELECT t.id FROM bank_transactions t JOIN accounts a ON a.id=t.account_id WHERE a.customer_id='" + owner + "'";
        capturePlan("ownership-only", base);
        capturePlan("accountId-filtered", base + " AND t.account_id='" + accounts.get("MAIN") + "'");
        capturePlan("combined-filter", base + " AND t.direction='OUT' AND t.status='COMPLETED' AND t.amount >= 10.00 AND t.occurred_at >= TIMESTAMPTZ '2025-01-01 00:00:00+02' AND t.occurred_at < TIMESTAMPTZ '2026-01-01 00:00:00+02'");
        capturePlan("searched", base + " AND (lower(t.merchant) LIKE '%market%' ESCAPE '\\' OR lower(t.reference) LIKE '%market%' ESCAPE '\\')");
    }

    private void capturePlan(String label, String sql) {
        List<String> plan = jdbc.query("EXPLAIN (ANALYZE, BUFFERS) " + sql, (rs, rowNum) -> rs.getString(1));
        assertThat(plan).isNotEmpty();
        System.out.println("EXPLAIN_CAPTURE " + label + "\n" + String.join("\n", plan));
    }

    @Test
    void transactionFixturesAreRepeatableCustomerScopedAndDoNotCreateAccountsOrUsers() {
        UUID a = customer("a@example.test", "CUSTOMER");
        UUID b = customer("b@example.test", "CUSTOMER");
        var accountsA = accountFixtures.createFixtureAccounts("a@example.test");
        var accountsB = accountFixtures.createFixtureAccounts("b@example.test");
        int users = jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class);
        int accounts = jdbc.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class);
        assertThat(fixturePort.getFixtureAccountIds("a@example.test")).isEqualTo(accountsA);
        assertThat(transactionFixtures.ensureCustomerFixtures("a@example.test")).isEqualTo(24);
        assertThat(transactionFixtures.ensureCustomerFixtures("b@example.test")).isEqualTo(24);
        assertThat(transactionFixtures.ensureCustomerFixtures("a@example.test")).isEqualTo(24);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bank_transactions", Integer.class)).isEqualTo(48);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class)).isEqualTo(accounts);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class)).isEqualTo(users);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT account_id) FROM bank_transactions WHERE account_id IN (?,?)", Integer.class,
                accountsA.get("MAIN"), accountsA.get("SAVINGS"))).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bank_transactions t JOIN accounts a ON a.id=t.account_id WHERE a.customer_id=?", Integer.class, a)).isEqualTo(24);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bank_transactions t JOIN accounts a ON a.id=t.account_id WHERE a.customer_id=?", Integer.class, b)).isEqualTo(24);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT occurred_at) FROM bank_transactions", Integer.class)).isGreaterThan(1);
    }

    @Test
    void fixtureRefusesNonCustomerAndMismatchWithoutReseeding() {
        customer("agent@example.test", "AGENT");
        UUID customer = customer("mismatch@example.test", "CUSTOMER");
        var accounts = accountFixtures.createFixtureAccounts("mismatch@example.test");
        transactionFixtures.ensureCustomerFixtures("mismatch@example.test");
        UUID first = jdbc.queryForObject("SELECT id FROM bank_transactions WHERE account_id=? ORDER BY reference LIMIT 1", UUID.class, accounts.get("MAIN"));
        jdbc.update("UPDATE bank_transactions SET merchant='Changed' WHERE id=?", first);
        assertThatThrownBy(() -> transactionFixtures.ensureCustomerFixtures("mismatch@example.test"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("immutable state");
        assertThatThrownBy(() -> transactionFixtures.ensureCustomerFixtures("agent@example.test"))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT merchant FROM bank_transactions WHERE id=?", String.class, first)).isEqualTo("Changed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bank_transactions", Integer.class)).isEqualTo(24);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE id=?", Integer.class, customer)).isEqualTo(1);
    }

    @Test
    void listAndDetailAreOwnerScopedAndResponsesNeverContainFullNumbers() throws Exception {
        UUID a = customer("a@example.test", "CUSTOMER");
        UUID b = customer("b@example.test", "CUSTOMER");
        var accountsA = accountFixtures.createFixtureAccounts("a@example.test");
        var accountsB = accountFixtures.createFixtureAccounts("b@example.test");
        transactionFixtures.ensureCustomerFixtures("a@example.test");
        transactionFixtures.ensureCustomerFixtures("b@example.test");
        List<String> allNumbers = jdbc.query("SELECT account_number FROM accounts", (row, index) -> row.getString(1));
        String cookie = login("a@example.test");

        HttpResponse<String> list = get("/api/v1/transactions?accountId=" + accountsA.get("MAIN") + "&direction=OUT&status=COMPLETED&search=Market&fromDate=2025-03-10&toDate=2025-03-10", cookie);
        JsonNode body = json.readTree(list.body());
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(list.headers().firstValue("X-Correlation-ID")).isPresent();
        assertThat(body.path("status").asBoolean()).isTrue();
        assertThat(body.at("/data/total").asLong()).isEqualTo(1);
        assertThat(body.at("/data/items/0/account/maskedNumber").asText()).isEqualTo("****" + accountNumber(accountsA.get("MAIN")).substring(6));
        assertThat(list.body()).doesNotContain(allNumbers.toArray(String[]::new));
        assertThat(list.body()).doesNotContain("accountNumber");

        UUID aTransaction = jdbc.queryForObject("SELECT t.id FROM bank_transactions t JOIN accounts a ON a.id=t.account_id WHERE a.customer_id=? LIMIT 1", UUID.class, a);
        UUID bTransaction = jdbc.queryForObject("SELECT t.id FROM bank_transactions t JOIN accounts a ON a.id=t.account_id WHERE a.customer_id=? LIMIT 1", UUID.class, b);
        HttpResponse<String> detail = get("/api/v1/transactions/" + aTransaction, cookie);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).doesNotContain(allNumbers.toArray(String[]::new));
        HttpResponse<String> foreignDetail = get("/api/v1/transactions/" + bTransaction, cookie);
        assertThat(foreignDetail.statusCode()).isEqualTo(404);
        assertThat(json.readTree(foreignDetail.body()).path("error_code").asInt()).isEqualTo(1003);
        HttpResponse<String> foreignAccount = get("/api/v1/transactions?accountId=" + accountsB.get("MAIN"), cookie);
        assertThat(foreignAccount.statusCode()).isEqualTo(404);
        assertThat(json.readTree(foreignAccount.body()).path("error_code").asInt()).isEqualTo(1003);
    }

    @Test
    void queryValidationSortingEmptyPagesRoleAndAuthenticationUseStableContracts() throws Exception {
        customer("customer@example.test", "CUSTOMER");
        customer("agent@example.test", "AGENT");
        var accounts = accountFixtures.createFixtureAccounts("customer@example.test");
        transactionFixtures.ensureCustomerFixtures("customer@example.test");
        String cookie = login("customer@example.test");
        HttpResponse<String> empty = get("/api/v1/transactions?page=10&size=20&accountId=" + accounts.get("MAIN"), cookie);
        assertThat(empty.statusCode()).isEqualTo(200);
        assertThat(json.readTree(empty.body()).at("/data/items").isEmpty()).isTrue();
        assertThat(json.readTree(empty.body()).at("/data/total").asLong()).isEqualTo(12);
        for (String path : List.of("?accountId=bad", "?accountId=" + accounts.get("MAIN") + "&accountId=" + accounts.get("SAVINGS"),
                "?fromDate=2025-01-01&toDate=2026-01-02", "?page=2147483647&size=100")) {
            HttpResponse<String> invalid = get("/api/v1/transactions" + path, cookie);
            assertThat(invalid.statusCode()).isEqualTo(400);
            assertThat(json.readTree(invalid.body()).path("error_code").asInt()).isEqualTo(1004);
        }
        assertThat(get("/api/v1/transactions/invalid", cookie).statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/transactions/10000000-0000-0000-0000-000000000001?accountId=" + accounts.get("MAIN"), cookie).statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/transactions", login("agent@example.test")).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/transactions", null).statusCode()).isEqualTo(401);
        HttpResponse<String> sorted = get("/api/v1/transactions?accountId=" + accounts.get("MAIN") + "&sortBy=occurredAt&sortDirection=ASC", cookie);
        assertThat(sorted.statusCode()).isEqualTo(200);
        assertThat(json.readTree(sorted.body()).at("/data/items/0/occurredAt").asText()).isEqualTo("2025-03-10T08:15:00Z");
        HttpResponse<String> literal = get("/api/v1/transactions?accountId=" + accounts.get("MAIN") + "&search=%25", cookie);
        assertThat(json.readTree(literal.body()).at("/data/total").asLong()).isZero();
    }

    @Test
    void databaseFailureIsUnavailableAndBothHandlersRenewActivity() throws Exception {
        UUID customer = customer("activity@example.test", "CUSTOMER");
        var accounts = accountFixtures.createFixtureAccounts("activity@example.test");
        transactionFixtures.ensureCustomerFixtures("activity@example.test");
        String cookie = login("activity@example.test");
        String sessionId = new String(java.util.Base64.getDecoder().decode(cookie.substring(cookie.indexOf('=') + 1)), java.nio.charset.StandardCharsets.UTF_8);
        long before = jdbc.queryForObject("SELECT last_authenticated_activity_at FROM auth_session_activity m JOIN spring_session s ON s.primary_id=m.session_primary_id WHERE s.session_id=?", Long.class, sessionId);
        Thread.sleep(20);
        assertThat(get("/api/v1/transactions?accountId=" + accounts.get("MAIN"), cookie).statusCode()).isEqualTo(200);
        long afterList = lastActivity(sessionId);
        assertThat(afterList).isGreaterThan(before);
        Thread.sleep(20);
        UUID transaction = jdbc.queryForObject("SELECT id FROM bank_transactions WHERE account_id=? LIMIT 1", UUID.class, accounts.get("MAIN"));
        assertThat(get("/api/v1/transactions/" + transaction, cookie).statusCode()).isEqualTo(200);
        assertThat(lastActivity(sessionId)).isGreaterThan(afterList);

        doThrow(new DataAccessResourceFailureException("private transaction database fault"))
                .when(transactions).count(org.mockito.ArgumentMatchers.eq(customer), org.mockito.ArgumentMatchers.any());
        HttpResponse<String> failed = get("/api/v1/transactions", cookie);
        assertThat(failed.statusCode()).isEqualTo(503);
        assertThat(json.readTree(failed.body()).path("error_code").asInt()).isEqualTo(3002);
        assertThat(failed.body()).doesNotContain("private transaction database fault");
    }

    private UUID customer(String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users(id,email,password_hash,role,enabled,created_at) VALUES(?,?,?,?,true,?)",
                id, email, passwords.encode(PASSWORD), role, java.sql.Timestamp.from(CREATED_AT));
        return id;
    }
    private void insertTransaction(UUID id, UUID account, String reference, String merchant, String amount, String direction, String status) {
        jdbc.update("INSERT INTO bank_transactions(id,account_id,reference,merchant,amount,currency,direction,status,occurred_at) VALUES(?,?,?,?,?,'ZAR',?,?,?)",
                id, account, reference, merchant, new BigDecimal(amount), direction, status, java.sql.Timestamp.from(CREATED_AT));
    }
    private String accountNumber(UUID accountId) {
        return jdbc.queryForObject("SELECT account_number FROM accounts WHERE id=?", String.class, accountId);
    }
    private long lastActivity(String sessionId) {
        return jdbc.queryForObject("SELECT last_authenticated_activity_at FROM auth_session_activity m JOIN spring_session s ON s.primary_id=m.session_primary_id WHERE s.session_id=?", Long.class, sessionId);
    }
    private String login(String email) throws Exception {
        HttpResponse<String> csrf = get("/api/v1/auth/csrf", null);
        String preAuth = cookie(csrf); String token = json.readTree(csrf.body()).at("/data/csrfToken").asText();
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/login")).timeout(Duration.ofSeconds(10))
                .header("Cookie", preAuth).header("X-CSRF-TOKEN", token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("email", email, "password", PASSWORD)))).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200); return cookie(response);
    }
    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET();
        if (cookie != null) request.header("Cookie", cookie);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    private static String cookie(HttpResponse<String> response) { return response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0]; }
}
