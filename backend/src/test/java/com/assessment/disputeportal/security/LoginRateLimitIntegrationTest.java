package com.assessment.disputeportal.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.assessment.disputeportal.security.authentication.VersionedPasswordEncoder;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LoginRateLimitIntegrationTest extends SessionIntegrationSupport {
  @Autowired VersionedPasswordEncoder passwords;

  @BeforeEach
  void clearUsers() {
    jdbc.update("DELETE FROM spring_session");
    jdbc.update("DELETE FROM app_users");
  }

  @Test
  void validLoginWorksBelowThresholdThenRepeatedFailuresReturnRetryable429() throws Exception {
    String email = "rate-limit-" + UUID.randomUUID() + "@example.test";
    String password = "isolated rate limit password 123";
    jdbc.update(
        "INSERT INTO app_users(id,email,password_hash,role,enabled,created_at) VALUES(?,?,?,?,?,now())",
        UUID.randomUUID(),
        email,
        passwords.encode(password),
        "CUSTOMER",
        true);

    var initial = request("GET", "/api/v1/auth/csrf", Map.of());
    var initialBody = body(initial);
    var valid = login(cookie(initial), initialBody.at("/data/csrfToken").asText(), email, password);
    assertThat(valid.statusCode()).isEqualTo(200);

    var bootstrap = request("GET", "/api/v1/auth/csrf", Map.of());
    String cookie = cookie(bootstrap);
    String token = body(bootstrap).at("/data/csrfToken").asText();
    for (int attempt = 1; attempt <= 5; attempt++) {
      var failed = login(cookie, token, email, "incorrect password");
      if (attempt < 5) {
        assertThat(failed.statusCode()).isEqualTo(401);
        assertThat(body(failed).path("error_code").asInt()).isEqualTo(1001);
      } else {
        assertThat(failed.statusCode()).isEqualTo(429);
        assertThat(body(failed).path("error_code").asInt()).isEqualTo(1005);
        assertThat(failed.headers().firstValue("Retry-After")).isPresent();
        assertThat(failed.headers().firstValue("Cache-Control")).contains("no-store");
      }
    }

    var throttled = login(cookie, token, email, password);
    assertThat(throttled.statusCode()).isEqualTo(429);
    assertThat(body(throttled).path("error_code").asInt()).isEqualTo(1005);
  }

  private HttpResponse<String> login(String cookie, String token, String email, String password)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
            .timeout(java.time.Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Cookie", cookie)
            .header("X-CSRF-TOKEN", token)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    json.writeValueAsString(Map.of("email", email, "password", password))))
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
