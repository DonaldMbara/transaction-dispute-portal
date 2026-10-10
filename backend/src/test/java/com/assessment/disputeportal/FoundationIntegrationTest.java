package com.assessment.disputeportal;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.flywaydb.core.Flyway;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class FoundationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Flyway flyway;

    @Autowired
    HealthEndpointGroups groups;

    @Autowired
    DataSource dataSource;

    @Autowired
    org.springframework.context.ApplicationContext context;

    @Test
    void operationalProvisioningIsInactiveAndUnreachableFromPortalHttp() {
        assertThat(context.getBeansOfType(com.assessment.disputeportal.auth.service.AccountAdministrationService.class)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class)).isZero();
        for (String path : new String[]{"/api/v1/auth/provision", "/provision"}) {
            assertThat(http.getForEntity(path, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(http.postForEntity(path, null, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class)).isZero();
    }

    @Test
    void readinessIncludesDatabaseAndLivenessExcludesIt() {
        assertThat(groups.get("readiness").isMember("db")).isTrue();
        assertThat(groups.get("liveness").isMember("db")).isFalse();
        assertThat(groups.get("liveness").isMember("livenessState")).isTrue();
    }

    @Test
    void schemaConstraintsHoldWithoutPersistingTestUsers() throws Exception {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var input = new ClassPathResource("app-users-constraints-jdbc.sql").getInputStream();
                 var statement = connection.createStatement()) {
                statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            } finally {
                connection.rollback();
            }
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class)).isZero();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void healthIsPublicAndOtherPathsAreDenied() {
        for (String path : new String[]{"/actuator/health",
                "/actuator/health/readiness", "/actuator/health/liveness"}) {
            ResponseEntity<JsonNode> response = http.getForEntity(path, JsonNode.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
            assertThat(response.getBody().has("components")).isFalse();
            assertThat(response.getBody().has("details")).isFalse();
        }
        for (String path : new String[]{"/api/v1/transactions", "/unrelated",
                "/actuator/env", "/actuator/health/db", "/login"}) {
            ResponseEntity<String> response = http.getForEntity(path, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getHeaders().getLocation()).isNull();
        }
    }

    @Test
    void healthDoesNotPermitMutatingRequests() {
        assertThat(http.postForEntity("/actuator/health", null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.postForEntity("/api/v1/disputes", null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void requiredFoundationMigrationCreatesUsersWithoutMappedEntities() {
        Integer successfulV1 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '1' AND script = 'V1__create_app_users.sql' "
                        + "AND success = TRUE", Integer.class);
        assertThat(successfulV1).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT to_regclass('public.app_users')::text", String.class))
                .isEqualTo("app_users");
    }
}
