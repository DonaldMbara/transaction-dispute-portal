package com.assessment.disputeportal.security;

import static org.assertj.core.api.Assertions.*;

import com.assessment.disputeportal.security.session.SessionActivityStore;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoginSessionLifecycleTest extends AuthenticationApiIntegrationTest {
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  SessionActivityStore activity;

  @Test
  void committedAccountChangeBeforeActivationCannotAuthenticate() throws Exception {
    account("CUSTOMER", true);
    org.mockito.Mockito.doAnswer(
            call -> {
              jdbc.update("UPDATE app_users SET auth_version=auth_version+1");
              return call.callRealMethod();
            })
        .when(activity)
        .activate(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyLong());
    var b = request("GET", "/api/v1/auth/csrf", Map.of());
    var result = login(cookie(b), body(b).at("/data/csrfToken").asText());
    assertThat(result.statusCode()).isEqualTo(401);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_session_activity", Integer.class))
        .isZero();
  }

  @Test
  void rotatesIdentifierAndRetiresPreLoginToken() throws Exception {
    account("CUSTOMER", true);
    var bootstrap = request("GET", "/api/v1/auth/csrf", Map.of());
    var old = cookie(bootstrap);
    var token = body(bootstrap).at("/data/csrfToken").asText();
    var result = login(old, token);
    assertThat(result.statusCode()).isEqualTo(200);
    var fresh = cookie(result);
    assertThat(fresh).isNotEqualTo(old);
    assertThat(request("GET", "/api/v1/auth/me", Map.of("Cookie", old)).statusCode())
        .isEqualTo(401);
    assertThat(login(fresh, token).statusCode()).isEqualTo(403);
    var next = request("GET", "/api/v1/auth/csrf", Map.of("Cookie", fresh));
    assertThat(login(fresh, body(next).at("/data/csrfToken").asText()).statusCode()).isEqualTo(200);
  }

  @Test
  void failedAccountSwitchPreservesExistingSessionButExpiredIdentityFails() throws Exception {
    account("CUSTOMER", true);
    var b = request("GET", "/api/v1/auth/csrf", Map.of());
    var r = login(cookie(b), body(b).at("/data/csrfToken").asText());
    var c = cookie(r);
    var token = request("GET", "/api/v1/auth/csrf", Map.of("Cookie", c));
    assertThat(
            post(
                    "/api/v1/auth/login",
                    c,
                    body(token).at("/data/csrfToken").asText(),
                    "{\"email\":\"unknown@example.test\",\"password\":\"isolated exact password 123\"}")
                .statusCode())
        .isEqualTo(401);
    assertThat(request("GET", "/api/v1/auth/me", Map.of("Cookie", c)).statusCode()).isEqualTo(200);
    jdbc.update("UPDATE auth_session_activity SET last_authenticated_activity_at=1");
    assertThat(request("GET", "/api/v1/auth/me", Map.of("Cookie", c)).statusCode()).isEqualTo(401);
  }
}
