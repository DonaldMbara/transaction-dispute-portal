package com.assessment.disputeportal.security;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;

@TestPropertySource(
    properties = {
      "spring.datasource.hikari.connection-timeout=1000",
      "spring.datasource.hikari.validation-timeout=500",
      "spring.datasource.hikari.data-source-properties.socketTimeout=1",
      "spring.datasource.hikari.data-source-properties.options=-c statement_timeout=1000"
    })
class AuthenticationDatabaseOutageTest extends AuthenticationApiIntegrationTest {
  @Test
  void isolatedDatabasePauseFailsClosedAndSamePersistedSessionRecovers() throws Exception {
    account("CUSTOMER", true);
    var bootstrap = request("GET", "/api/v1/auth/csrf", Map.of());
    var login = login(cookie(bootstrap), body(bootstrap).at("/data/csrfToken").asText());
    var c = cookie(login);
    var csrf = request("GET", "/api/v1/auth/csrf", Map.of("Cookie", c));
    var token = body(csrf).at("/data/csrfToken").asText();
    var docker = DockerClientFactory.instance().client();
    String id = postgres.getContainerId();
    docker.pauseContainerCmd(id).exec();
    try {
      var me = request("GET", "/api/v1/auth/me", Map.of("Cookie", c));
      assertThat(me.statusCode()).isEqualTo(503);
      assertThat(body(me).path("error_code").asInt()).isEqualTo(3002);
      assertThat(post("/api/v1/auth/logout", c, token, "").statusCode()).isEqualTo(503);
      assertThat(login(c, token).statusCode()).isEqualTo(503);
      assertThat(request("GET", "/api/v1/auth/csrf", Map.of()).statusCode()).isEqualTo(503);
      assertThat(request("GET", "/actuator/health/readiness", Map.of("Cookie", c)).statusCode())
          .isEqualTo(503);
      var live = request("GET", "/actuator/health/liveness", Map.of("Cookie", c));
      assertThat(live.statusCode()).isEqualTo(200);
      assertThat(body(live).path("status").asText()).isEqualTo("UP");
    } finally {
      docker.unpauseContainerCmd(id).exec();
    }
    boolean recovered = false;
    for (int attempt = 0; attempt < 20; attempt++) {
      if (request("GET", "/actuator/health/readiness", Map.of()).statusCode() == 200) {
        recovered = true;
        break;
      }
      Thread.sleep(200);
    }
    assertThat(recovered).isTrue();
    assertThat(request("GET", "/api/v1/auth/me", Map.of("Cookie", c)).statusCode()).isEqualTo(200);
    assertThat(post("/api/v1/auth/logout", c, token, "").statusCode()).isEqualTo(200);
    assertThat(request("GET", "/api/v1/auth/me", Map.of("Cookie", c)).statusCode()).isEqualTo(401);
  }
}
