package com.assessment.disputeportal;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

// A separate container and context prevent the outage from poisoning ordinary tests.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.hikari.connection-timeout=1000",
                "spring.datasource.hikari.validation-timeout=500"})
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DatabaseOutageTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    HealthContributorRegistry contributors;

    @Test
    void databaseOutageDegradesReadinessButNotLiveness() {
        assertThat(http.getForEntity("/actuator/health/readiness", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        postgres.stop();
        // Inspect the DB indicator privately; public responses remain status-only.
        assertThat(contributors.getContributor("db")).isInstanceOf(HealthIndicator.class);
        assertThat(((HealthIndicator) contributors.getContributor("db")).health().getStatus())
                .isEqualTo(Status.DOWN);
        for (String path : new String[]{"/actuator/health", "/actuator/health/readiness"}) {
            var response = http.getForEntity(path, JsonNode.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().path("status").asText()).isEqualTo("DOWN");
            assertThat(response.getBody().has("components")).isFalse();
        }
        var live = http.getForEntity("/actuator/health/liveness", JsonNode.class);
        assertThat(live.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(live.getBody().path("status").asText()).isEqualTo("UP");
    }
}
