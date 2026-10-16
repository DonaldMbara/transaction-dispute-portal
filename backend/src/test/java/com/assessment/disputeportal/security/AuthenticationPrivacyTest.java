package com.assessment.disputeportal.security;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthenticationPrivacyTest extends AuthenticationApiIntegrationTest {
  @Test
  void representativeResponsesAndAuditLogsExcludeCredentialsCookiesTokensAndHashes()
      throws Exception {
    var logger =
        (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("authentication.audit");
    var capture =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    capture.start();
    logger.addAppender(capture);
    try {
      account("CUSTOMER", true);
      String hash = jdbc.queryForObject("SELECT password_hash FROM app_users", String.class);
      var b = request("GET", "/api/v1/auth/csrf", Map.of());
      var token = body(b).at("/data/csrfToken").asText();
      var old = cookie(b);
      var success = login(old, token);
      var c = cookie(success);
      var fresh = request("GET", "/api/v1/auth/csrf", Map.of("Cookie", c));
      var newToken = body(fresh).at("/data/csrfToken").asText();
      var denied =
          post(
              "/api/v1/auth/login",
              c,
              newToken,
              "{\"email\":\"private@example.test\",\"password\":\"private rejected password\"}");
      var logout = post("/api/v1/auth/logout", c, newToken, "");
      for (var response : java.util.List.of(success, denied, logout)) {
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("X-Correlation-ID").orElseThrow())
            .matches("[A-Za-z0-9_-]{1,64}");
        assertThat(response.body())
            .doesNotContain(
                PASSWORD,
                hash,
                token,
                newToken,
                old,
                c,
                "private@example.test",
                "private rejected password");
      }
      String logs =
          capture.list.stream()
              .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
              .collect(java.util.stream.Collectors.joining("\n"));
      assertThat(logs)
          .contains(
              "operation=login outcome=success",
              "operation=login outcome=rejected",
              "operation=logout outcome=success",
              "timestamp=");
      assertThat(logs).contains("account=");
      assertThat(logs)
          .doesNotContain(
              PASSWORD,
              hash,
              token,
              newToken,
              sessionId(old),
              sessionId(c),
              "private@example.test",
              "private rejected password");
    } finally {
      logger.detachAppender(capture);
      capture.stop();
    }
  }
}
