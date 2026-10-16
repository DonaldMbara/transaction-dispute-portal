package com.assessment.disputeportal.security;

import static org.assertj.core.api.Assertions.*;

import com.assessment.disputeportal.security.authentication.VersionedPasswordEncoder;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

class AuthenticationApiIntegrationTest extends SessionIntegrationSupport {
  @Autowired VersionedPasswordEncoder passwords;
  static final String PASSWORD = "isolated exact password 123";

  @BeforeEach
  void reset() {
    jdbc.update("DELETE FROM spring_session");
    jdbc.update("DELETE FROM app_users");
  }

  UUID account(String role, boolean enabled) {
    var id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app_users(id,email,password_hash,role,enabled,created_at) VALUES(?,?,?,?,?,now())",
        id,
        "login@example.test",
        passwords.encode(PASSWORD),
        role,
        enabled);
    return id;
  }

  HttpResponse<String> post(String path, String cookie, String token, String payload)
      throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(java.time.Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload));
    if (cookie != null) b.header("Cookie", cookie);
    if (token != null) b.header("X-CSRF-TOKEN", token);
    return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> login(String cookie, String token) throws Exception {
    return post(
        "/api/v1/auth/login",
        cookie,
        token,
        json.writeValueAsString(Map.of("email", " LOGIN@EXAMPLE.TEST ", "password", PASSWORD)));
  }

  @Test
  void bothRolesLoginMinimalIdentityAndPersistCredentialFreeContext() throws Exception {
    for (String role : List.of("CUSTOMER", "AGENT")) {
      reset();
      var id = account(role, true);
      var csrf = request("GET", "/api/v1/auth/csrf", Map.of());
      var result = login(cookie(csrf), body(csrf).at("/data/csrfToken").asText());
      assertThat(result.statusCode()).isEqualTo(200);
      assertThat(body(result).path("data").size()).isEqualTo(3);
      assertThat(body(result).at("/data/id").asText()).isEqualTo(id.toString());
      assertThat(body(result).at("/data/role").asText()).isEqualTo(role);
      assertThat(request("GET", "/api/v1/auth/me", Map.of("Cookie", cookie(result))).statusCode())
          .isEqualTo(200);
      org.springframework.session.Session persisted = sessions.findById(sessionId(cookie(result)));
      org.springframework.security.core.context.SecurityContext context =
          persisted.getAttribute("SPRING_SECURITY_CONTEXT");
      assertThat(context.getAuthentication().getCredentials()).isNull();
      assertThat(context.getAuthentication().getPrincipal())
          .isInstanceOf(com.assessment.disputeportal.security.session.SessionPrincipal.class);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_session_activity", Integer.class))
          .isEqualTo(1);
      assertThat(result.body()).doesNotContain("password", "authVersion", "sessionId");
    }
  }

  @Test
  void genericFailuresAndStrictMalformedInputsNeverActivate() throws Exception {
    account("CUSTOMER", true);
    var csrf = request("GET", "/api/v1/auth/csrf", Map.of());
    var c = cookie(csrf);
    var t = body(csrf).at("/data/csrfToken").asText();
    String first = null;
    for (String email : List.of("unknown@example.test", "login@example.test")) {
      var r =
          post(
              "/api/v1/auth/login",
              c,
              t,
              json.writeValueAsString(
                  Map.of("email", email, "password", "wrong isolated password")));
      assertThat(r.statusCode()).isEqualTo(401);
      if (first == null) first = r.body();
      else assertThat(r.body()).isEqualTo(first);
    }
    jdbc.update("UPDATE app_users SET enabled=false");
    var disabled = login(c, t);
    assertThat(disabled.statusCode()).isEqualTo(401);
    assertThat(disabled.body()).isEqualTo(first);
    for (String value :
        List.of(
            "null",
            "{\"email\":123456789012345,\"password\":123456789012345}",
            "{}",
            "{\"email\":\"login@example.test\",\"password\":\"\"}",
            "{\"email\":\"login@example.test\",\"password\":\"a" + "a".repeat(128) + "\"}",
            "{\"email\":\"login@example.test\",\"password\":\"isolated exact password 123\",\"role\":\"AGENT\"}"))
      assertThat(post("/api/v1/auth/login", c, t, value).statusCode()).isEqualTo(400);
    assertThat(
            post(
                    "/api/v1/auth/login",
                    c,
                    t,
                    "{\"email\":\"login@example.test\",\"password\":\"short\"}")
                .statusCode())
        .isEqualTo(401);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_session_activity", Integer.class))
        .isZero();
    assertThat(request("GET", "/api/v1/auth/me", Map.of("Cookie", c)).statusCode()).isEqualTo(401);
  }
}
