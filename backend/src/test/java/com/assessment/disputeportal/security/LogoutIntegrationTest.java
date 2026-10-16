package com.assessment.disputeportal.security;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LogoutIntegrationTest extends AuthenticationApiIntegrationTest {
  @Test
  void failedDeletionIsUnavailableAndDoesNotClaimLogout() throws Exception {
    account("CUSTOMER", true);
    var b = request("GET", "/api/v1/auth/csrf", Map.of());
    var login = login(cookie(b), body(b).at("/data/csrfToken").asText());
    var c = cookie(login);
    var token = request("GET", "/api/v1/auth/csrf", Map.of("Cookie", c));
    org.mockito.Mockito.doThrow(
            new org.springframework.dao.DataAccessResourceFailureException("private failure"))
        .when(sessions)
        .deleteById(sessionId(c));
    var result = post("/api/v1/auth/logout", c, body(token).at("/data/csrfToken").asText(), "");
    assertThat(result.statusCode()).isEqualTo(503);
    assertThat(body(result).path("error_code").asInt()).isEqualTo(3002);
    assertThat(result.body()).doesNotContain("private failure");
    org.mockito.Mockito.reset(sessions);
    assertThat(request("GET", "/api/v1/auth/me", Map.of("Cookie", c)).statusCode()).isEqualTo(200);
  }

  @Test
  void confirmedLogoutDeletesCurrentSessionOnlyAndExactCookieScope() throws Exception {
    account("AGENT", true);
    var b = request("GET", "/api/v1/auth/csrf", Map.of());
    var first = login(cookie(b), body(b).at("/data/csrfToken").asText());
    var b2 = request("GET", "/api/v1/auth/csrf", Map.of());
    var second = login(cookie(b2), body(b2).at("/data/csrfToken").asText());
    var c = cookie(first);
    var token = request("GET", "/api/v1/auth/csrf", Map.of("Cookie", c));
    var t = body(token).at("/data/csrfToken").asText();
    assertThat(request("GET", "/api/v1/auth/logout", Map.of("Cookie", c)).statusCode())
        .isEqualTo(403);
    assertThat(post("/api/v1/auth/logout", c, null, "").statusCode()).isEqualTo(403);
    assertThat(request("GET", "/api/v1/auth/me", Map.of("Cookie", c)).statusCode()).isEqualTo(200);
    var result = post("/api/v1/auth/logout", c, t, "");
    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(body(result).path("data").isNull()).isTrue();
    var headers = result.headers().allValues("set-cookie");
    assertThat(headers).isNotEmpty();
    for (String header : headers)
      assertThat(header)
          .contains("SESSION=", "Path=/api/v1", "Max-Age=0", "Secure", "HttpOnly", "SameSite=Lax")
          .doesNotContain("Domain=");
    assertThat(sessions.findById(sessionId(c))).isNull();
    assertThat(request("GET", "/api/v1/auth/me", Map.of("Cookie", c)).statusCode()).isEqualTo(401);
    assertThat(request("GET", "/api/v1/auth/me", Map.of("Cookie", cookie(second))).statusCode())
        .isEqualTo(200);
    assertThat(post("/api/v1/auth/logout", c, t, "").statusCode()).isEqualTo(403);
    var anon = request("GET", "/api/v1/auth/csrf", Map.of());
    assertThat(
            post("/api/v1/auth/logout", cookie(anon), body(anon).at("/data/csrfToken").asText(), "")
                .statusCode())
        .isEqualTo(200);
  }
}
