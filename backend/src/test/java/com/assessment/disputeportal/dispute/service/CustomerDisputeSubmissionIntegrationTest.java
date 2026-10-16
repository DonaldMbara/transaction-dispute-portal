package com.assessment.disputeportal.dispute.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.assessment.disputeportal.account.cli.AccountFixtureService;
import com.assessment.disputeportal.security.authentication.VersionedPasswordEncoder;
import com.assessment.disputeportal.transaction.cli.TransactionFixtureService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class CustomerDisputeSubmissionIntegrationTest {
    private static final String PASSWORD = "dispute integration password";
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
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach void clean() {
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM idempotency_records");
        jdbc.update("DELETE FROM dispute_events");
        jdbc.update("DELETE FROM disputes");
        jdbc.update("DELETE FROM bank_transactions");
        jdbc.update("DELETE FROM accounts");
        jdbc.update("DELETE FROM app_users");
    }

    @Test
    void createsOpenDisputeAndReplaysOnlyAnIdenticalIdempotentSubmission() throws Exception {
        customer("customer@example.test", "CUSTOMER");
        var accounts = accountFixtures.createFixtureAccounts("customer@example.test");
        transactionFixtures.ensureCustomerFixtures("customer@example.test");
        UUID transactionId = jdbc.queryForObject("SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' LIMIT 1",
                UUID.class, accounts.get("MAIN"));
        Session session = login("customer@example.test");
        String body = json.writeValueAsString(Map.of("transactionId", transactionId,
                "reason", "DUPLICATE_CHARGE", "description", "  Charged twice.  "));

        HttpResponse<String> created = post(session, "create-key", body);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        JsonNode createdJson = json.readTree(created.body());
        UUID disputeId = UUID.fromString(createdJson.at("/data/id").asText());
        assertThat(createdJson.at("/data/status").asText()).isEqualTo("OPEN");
        assertThat(createdJson.at("/data/description").asText()).isEqualTo("Charged twice.");
        assertThat(created.headers().firstValue("Location").orElseThrow()).isEqualTo("/api/v1/disputes/" + disputeId);
        assertThat(created.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(created.headers().firstValue("X-Correlation-ID")).isPresent();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM disputes WHERE id=? AND status='OPEN' AND version=0", Integer.class, disputeId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dispute_events WHERE dispute_id=? AND event_type='CREATED' AND dispute_version=0", Integer.class, disputeId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_records WHERE user_id=(SELECT id FROM app_users WHERE email=?)", Integer.class, "customer@example.test")).isEqualTo(1);

        HttpResponse<String> replay = post(session, "create-key", body.replace("  Charged twice.  ", "Charged twice."));
        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(json.readTree(replay.body()).at("/data/id").asText()).isEqualTo(disputeId.toString());
        assertThat(replay.headers().firstValue("Location")).isEqualTo(created.headers().firstValue("Location"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM disputes", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dispute_events", Integer.class)).isEqualTo(1);

        HttpResponse<String> changedKeyPayload = post(session, "create-key", json.writeValueAsString(Map.of(
                "transactionId", transactionId, "reason", "OTHER", "description", "Different request")));
        assertThat(changedKeyPayload.statusCode()).isEqualTo(409);
        assertThat(json.readTree(changedKeyPayload.body()).path("error_code").asInt()).isEqualTo(2004);
        HttpResponse<String> duplicate = post(session, "another-key", body);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(json.readTree(duplicate.body()).path("error_code").asInt()).isEqualTo(2003);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM disputes", Integer.class)).isEqualTo(1);
    }

    @Test
    void enforcesCustomerRoleCsrfOwnershipEligibilityAndInputBounds() throws Exception {
        customer("customer@example.test", "CUSTOMER");
        customer("other@example.test", "CUSTOMER");
        customer("agent@example.test", "AGENT");
        var mine = accountFixtures.createFixtureAccounts("customer@example.test");
        var theirs = accountFixtures.createFixtureAccounts("other@example.test");
        transactionFixtures.ensureCustomerFixtures("customer@example.test");
        transactionFixtures.ensureCustomerFixtures("other@example.test");
        UUID own = jdbc.queryForObject("SELECT id FROM bank_transactions WHERE account_id=? AND status='PENDING' LIMIT 1", UUID.class, mine.get("MAIN"));
        UUID foreign = jdbc.queryForObject("SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' LIMIT 1", UUID.class, theirs.get("MAIN"));
        String invalidStatus = "{\"transactionId\":\"" + own + "\",\"reason\":\"OTHER\",\"description\":\"  \"}";

        Session session = login("customer@example.test");
        HttpResponse<String> noCsrf = postWithoutCsrf(session, "csrf-key", invalidStatus);
        assertThat(noCsrf.statusCode()).isEqualTo(403);
        assertThat(json.readTree(noCsrf.body()).path("error_code").asInt()).isEqualTo(1010);
        HttpResponse<String> ineligible = post(session, "pending-key", invalidStatus);
        assertThat(ineligible.statusCode()).isEqualTo(400);
        assertThat(json.readTree(ineligible.body()).path("error_code").asInt()).isEqualTo(1004);

        String validForeign = json.writeValueAsString(Map.of("transactionId", foreign, "reason", "OTHER", "description", "Not mine"));
        HttpResponse<String> notOwned = post(session, "foreign-key", validForeign);
        assertThat(notOwned.statusCode()).isEqualTo(404);
        assertThat(json.readTree(notOwned.body()).path("error_code").asInt()).isEqualTo(1003);

        UUID pending = jdbc.queryForObject("SELECT id FROM bank_transactions WHERE account_id=? AND status='PENDING' LIMIT 1", UUID.class, mine.get("MAIN"));
        String pendingBody = json.writeValueAsString(Map.of("transactionId", pending, "reason", "OTHER", "description", "Pending transaction"));
        HttpResponse<String> ineligibleStatus = post(session, "pending-status-key", pendingBody);
        assertThat(ineligibleStatus.statusCode()).isEqualTo(422);
        assertThat(json.readTree(ineligibleStatus.body()).path("error_code").asInt()).isEqualTo(2002);

        UUID incoming = jdbc.queryForObject("SELECT id FROM bank_transactions WHERE account_id=? AND status='COMPLETED' AND direction='IN' LIMIT 1", UUID.class, mine.get("MAIN"));
        String incomingBody = json.writeValueAsString(Map.of("transactionId", incoming, "reason", "OTHER", "description", "Incorrect incoming credit"));
        HttpResponse<String> incomingIneligible = post(session, "incoming-key", incomingBody);
        assertThat(incomingIneligible.statusCode()).isEqualTo(422);
        assertThat(json.readTree(incomingIneligible.body()).path("error_code").asInt()).isEqualTo(2002);

        HttpResponse<String> wrongRole = post(login("agent@example.test"), "agent-key", validForeign);
        assertThat(wrongRole.statusCode()).isEqualTo(403);
        assertThat(json.readTree(wrongRole.body()).path("error_code").asInt()).isEqualTo(1002);
        HttpResponse<String> anonymousCsrf = get("/api/v1/auth/csrf", null);
        HttpResponse<String> anonymous = sendPost(cookie(anonymousCsrf),
                json.readTree(anonymousCsrf.body()).at("/data/csrfToken").asText(), "anonymous-key", validForeign);
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(json.readTree(anonymous.body()).path("error_code").asInt()).isEqualTo(1001);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM disputes", Integer.class)).isZero();
    }

    private UUID customer(String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users(id,email,password_hash,role,enabled,created_at) VALUES(?,?,?,?,true,?)",
                id, email, passwords.encode(PASSWORD), role, java.sql.Timestamp.from(CREATED_AT));
        return id;
    }

    private Session login(String email) throws Exception {
        HttpResponse<String> preflight = get("/api/v1/auth/csrf", null);
        String preCookie = cookie(preflight);
        String preToken = json.readTree(preflight.body()).at("/data/csrfToken").asText();
        HttpRequest login = HttpRequest.newBuilder(uri("/api/v1/auth/login")).timeout(Duration.ofSeconds(10))
                .header("Cookie", preCookie).header("X-CSRF-TOKEN", preToken).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("email", email, "password", PASSWORD)))).build();
        HttpResponse<String> response = client.send(login, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String sessionCookie = cookie(response);
        HttpResponse<String> csrf = get("/api/v1/auth/csrf", sessionCookie);
        return new Session(sessionCookie, json.readTree(csrf.body()).at("/data/csrfToken").asText());
    }

    private HttpResponse<String> post(Session session, String key, String body) throws Exception {
        return sendPost(session.cookie(), session.token(), key, body);
    }
    private HttpResponse<String> postWithoutCsrf(Session session, String key, String body) throws Exception {
        return sendPost(session.cookie(), null, key, body);
    }
    private HttpResponse<String> postWithoutCookie(String key, String body) throws Exception {
        return sendPost(null, null, key, body);
    }
    private HttpResponse<String> sendPost(String cookie, String token, String key, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri("/api/v1/disputes")).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) builder.header("Cookie", cookie);
        if (token != null) builder.header("X-CSRF-TOKEN", token);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET();
        if (cookie != null) builder.header("Cookie", cookie);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    private static String cookie(HttpResponse<String> response) { return response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0]; }
    private record Session(String cookie, String token) {}
}
