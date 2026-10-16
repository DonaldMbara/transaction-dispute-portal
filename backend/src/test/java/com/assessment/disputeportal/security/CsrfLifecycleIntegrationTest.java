package com.assessment.disputeportal.security;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CsrfLifecycleIntegrationTest extends AuthenticationApiIntegrationTest {
  @Test
  void foreignTokensAndExpiredBootstrapCannotAuthenticateOrRevive() throws Exception {
    account("CUSTOMER", true);
    var a = request("GET", "/api/v1/auth/csrf", Map.of());
    var b = request("GET", "/api/v1/auth/csrf", Map.of());
    assertThat(login(cookie(a), body(b).at("/data/csrfToken").asText()).statusCode())
        .isEqualTo(403);
    var result = login(cookie(a), body(a).at("/data/csrfToken").asText());
    var c = cookie(result);
    jdbc.update("UPDATE auth_session_activity SET last_authenticated_activity_at=1");
    var fresh = request("GET", "/api/v1/auth/csrf", Map.of("Cookie", c));
    assertThat(fresh.statusCode()).isEqualTo(200);
    assertThat(cookie(fresh)).isNotEqualTo(c);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_session_activity", Integer.class))
        .isZero();
    assertThat(request("GET", "/api/v1/auth/me", Map.of("Cookie", cookie(fresh))).statusCode())
        .isEqualTo(401);
    assertThat(login(cookie(fresh), body(fresh).at("/data/csrfToken").asText()).statusCode())
        .isEqualTo(200);
  }
}
