package com.assessment.disputeportal.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.assessment.disputeportal.auth.model.AccountRole;
import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.common.web.ApiErrorWriter;
import com.assessment.disputeportal.security.session.AccountSessionValidationFilter;
import com.assessment.disputeportal.security.session.JdbcSessionActivityStore;
import com.assessment.disputeportal.security.session.SessionActivityStore;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import com.assessment.disputeportal.security.web.AuthenticatedActivity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@Import(AuthenticationActivityIntegrationTest.FixtureConfiguration.class)
class AuthenticationActivityIntegrationTest extends SessionIntegrationSupport {
  @Autowired FixtureConfiguration fixture;
  @MockitoSpyBean JdbcSessionActivityStore activity;

  record AccountFixture(UUID account, String sessionId, String cookie, long originalActivity) {
    @Override
    public String toString() {
      return "AccountFixture[account=" + account + "]";
    }
  }

  long now() {
    return jdbc.queryForObject(
        "SELECT floor(extract(epoch FROM clock_timestamp())*1000)::bigint", Long.class);
  }

  @BeforeEach
  void clean() {
    jdbc.update("DELETE FROM spring_session");
    jdbc.update("DELETE FROM app_users");
    fixture.calls.set(0);
    fixture.observed.set(0);
  }

  AccountFixture account(String role) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app_users(id,email,password_hash,role,enabled,created_at) VALUES(?,?,?, ?,true,now())",
        id,
        id + "@example.test",
        "{pbkdf2-sha256-v1}" + "0".repeat(96),
        role);
    org.springframework.session.Session session = sessions.createSession();
    session.setAttribute(
        "SPRING_SECURITY_CONTEXT",
        new SecurityContextImpl(
            UsernamePasswordAuthenticationToken.authenticated(
                new SessionPrincipal(id),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_FORGED")))));
    long original = now() - 10_000;
    jdbc.update(
        "INSERT INTO auth_session_activity(session_primary_id,account_id,auth_version,last_authenticated_activity_at) SELECT primary_id,?,0,? FROM spring_session WHERE session_id=?",
        id,
        original,
        session.getId());
    return new AccountFixture(
        id,
        session.getId(),
        "SESSION="
            + Base64.getEncoder()
                .encodeToString(session.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        original);
  }

  long last(AccountFixture account) {
    return jdbc.queryForObject(
        "SELECT last_authenticated_activity_at FROM auth_session_activity WHERE account_id=?",
        Long.class,
        account.account());
  }

  Map<String, String> headers(AccountFixture account) {
    return Map.of("Cookie", account.cookie());
  }

  @Test
  void authorizedAdmissionRenewsBeforeLaterSuccessAndFailureAndMatchesFrameworkTime()
      throws Exception {
    var account = account("CUSTOMER");
    for (int status : new int[] {200, 400, 404, 409, 412, 422, 500}) {
      jdbc.update(
          "UPDATE auth_session_activity SET last_authenticated_activity_at=? WHERE account_id=?",
          now() - 10_000,
          account.account());
      var result = request("GET", "/api/v1/test/eligible?status=" + status, headers(account));
      assertThat(result.statusCode()).isEqualTo(status);
      assertThat(last(account)).isEqualTo(fixture.observed.get());
      assertThat(last(account))
          .isEqualTo(
              jdbc.queryForObject(
                  "SELECT last_access_time FROM spring_session WHERE session_id=?",
                  Long.class,
                  account.sessionId()));
      assertThat(last(account)).isGreaterThan(account.originalActivity());
    }
    assertThat(fixture.calls.get()).isEqualTo(7);
  }

  @Test
  void realArgumentValidationFailureCountsButMissingCsrfDoesNot() throws Exception {
    var account = account("CUSTOMER");
    var bootstrap = request("GET", "/api/v1/auth/csrf", headers(account));
    String token = body(bootstrap).path("data").path("csrfToken").asText();
    var missing = request("POST", "/api/v1/test/eligible", headers(account));
    assertThat(missing.statusCode()).isEqualTo(403);
    assertThat(last(account)).isEqualTo(account.originalActivity());
    var validation =
        client.send(
            java.net.http.HttpRequest.newBuilder(
                    java.net.URI.create("http://localhost:" + port + "/api/v1/test/eligible"))
                .header("Cookie", account.cookie())
                .header("X-CSRF-TOKEN", token)
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"value\":\"\"}"))
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
    assertThat(validation.statusCode()).isEqualTo(400);
    assertThat(fixture.calls.get()).isZero();
    assertThat(last(account)).isGreaterThan(account.originalActivity());
    assertThat(last(account))
        .isEqualTo(
            jdbc.queryForObject(
                "SELECT last_access_time FROM spring_session WHERE session_id=?",
                Long.class,
                account.sessionId()));
  }

  @Test
  void excludedBackgroundBootstrapHealthUnmatchedWrongMethodAndWrongRoleDoNotRenew()
      throws Exception {
    var account = account("CUSTOMER");
    for (String path :
        new String[] {
          "/api/v1/auth/csrf",
          "/actuator/health",
          "/actuator/health/liveness",
          "/api/v1/test/poll",
          "/api/v1/test/unmatched",
          "/api/v1/auth/logout",
          "/api/v1/test/agent"
        }) {
      request("GET", path, Map.of("Cookie", account.cookie(), "X-User-Activity", "true"));
      assertThat(last(account)).isEqualTo(account.originalActivity());
    }
    request("OPTIONS", "/api/v1/test/eligible", headers(account));
    assertThat(last(account)).isEqualTo(account.originalActivity());
    var agent = account("AGENT");
    assertThat(request("GET", "/api/v1/test/eligible", headers(agent)).statusCode()).isEqualTo(403);
    assertThat(last(agent)).isEqualTo(agent.originalActivity());
    assertThat(fixture.calls.get()).isZero();
    // The stored forged authority is replaced with the current database AGENT role.
    assertThat(request("GET", "/api/v1/test/agent", headers(agent)).statusCode()).isEqualTo(200);
  }

  @Test
  void expiredActivityAndFrameworkExpiryDenyBeforeHandlerWithoutWaiting() throws Exception {
    var account = account("CUSTOMER");
    jdbc.update(
        "UPDATE auth_session_activity SET last_authenticated_activity_at=? WHERE account_id=?",
        now() - SessionActivityStore.IDLE_MILLIS,
        account.account());
    assertThat(request("GET", "/api/v1/test/eligible", headers(account)).statusCode())
        .isEqualTo(401);
    assertThat(fixture.calls.get()).isZero();
    var frameworkExpired = account("CUSTOMER");
    jdbc.update(
        "UPDATE spring_session SET last_access_time=?,expiry_time=? WHERE session_id=?",
        now() - 1_800_001,
        now() - 1,
        frameworkExpired.sessionId());
    assertThat(request("GET", "/api/v1/test/eligible", headers(frameworkExpired)).statusCode())
        .isEqualTo(401);
    assertThat(fixture.calls.get()).isZero();
  }

  @Test
  void disabledDeletedChangedVersionMismatchedOrMissingMetadataAllDeny() throws Exception {
    for (String change :
        new String[] {"disabled", "reenabled", "deleted", "version", "missing", "mismatch"}) {
      var account = account("CUSTOMER");
      switch (change) {
        case "disabled" ->
            jdbc.update(
                "UPDATE app_users SET enabled=false,auth_version=auth_version+1 WHERE id=?",
                account.account());
        case "reenabled" -> {
          jdbc.update(
              "UPDATE app_users SET enabled=false,auth_version=auth_version+1 WHERE id=?",
              account.account());
          jdbc.update(
              "UPDATE app_users SET enabled=true,auth_version=auth_version+1 WHERE id=?",
              account.account());
        }
        case "deleted" -> jdbc.update("DELETE FROM app_users WHERE id=?", account.account());
        case "version" ->
            jdbc.update(
                "UPDATE app_users SET role='AGENT',auth_version=auth_version+1 WHERE id=?",
                account.account());
        case "missing" ->
            jdbc.update("DELETE FROM auth_session_activity WHERE account_id=?", account.account());
        case "mismatch" -> {
          var other = account("CUSTOMER");
          jdbc.update(
              "UPDATE auth_session_activity SET account_id=? WHERE account_id=?",
              other.account(),
              account.account());
        }
      }
      assertThat(request("GET", "/api/v1/test/eligible", headers(account)).statusCode())
          .isEqualTo(401);
    }
    assertThat(fixture.calls.get()).isZero();
  }

  @Test
  void unsupportedDatabaseRoleIsDeniedSafelyAndRevokedBootstrapDowngradesToAnonymous()
      throws Exception {
    var account = account("CUSTOMER");
    jdbc.execute("ALTER TABLE app_users DROP CONSTRAINT ck_app_users_role");
    try {
      jdbc.update("UPDATE app_users SET role='UNSUPPORTED' WHERE id=?", account.account());
      assertThat(request("GET", "/api/v1/test/eligible", headers(account)).statusCode())
          .isEqualTo(401);
    } finally {
      jdbc.update("UPDATE app_users SET role='CUSTOMER' WHERE role='UNSUPPORTED'");
      jdbc.execute(
          "ALTER TABLE app_users ADD CONSTRAINT ck_app_users_role CHECK (role IN ('CUSTOMER','AGENT'))");
    }
    var revoked = account("CUSTOMER");
    jdbc.update(
        "UPDATE auth_session_activity SET last_authenticated_activity_at=? WHERE account_id=?",
        now() - 1_800_000,
        revoked.account());
    var anonymous = request("GET", "/api/v1/auth/csrf", headers(revoked));
    assertThat(anonymous.statusCode()).isEqualTo(200);
    assertThat(cookie(anonymous).equals(revoked.cookie())).isFalse();
    assertThat(body(anonymous).path("data").fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("csrfToken", "headerName");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auth_session_activity WHERE account_id=?",
                Integer.class,
                revoked.account()))
        .isZero();
  }

  @Test
  void concurrentRenewalIsMonotonicAndLogoutRevocationCannotBeResurrected() throws Exception {
    var account = account("CUSTOMER");
    long timestamp = now();
    var barrier = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var older =
          pool.submit(
              () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return activity.renew(
                    account.sessionId(),
                    account.account(),
                    0,
                    AccountRole.CUSTOMER,
                    timestamp - 9000);
              });
      var newer =
          pool.submit(
              () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return activity.renew(
                    account.sessionId(),
                    account.account(),
                    0,
                    AccountRole.CUSTOMER,
                    timestamp - 1000);
              });
      assertThat(older.get(15, TimeUnit.SECONDS)).isTrue();
      assertThat(newer.get(15, TimeUnit.SECONDS)).isTrue();
    }
    assertThat(last(account)).isEqualTo(timestamp - 1000);
    jdbc.update("UPDATE app_users SET auth_version=1 WHERE id=?", account.account());
    assertThat(
            activity.renew(
                account.sessionId(), account.account(), 0, AccountRole.CUSTOMER, timestamp))
        .isFalse();
    assertThat(last(account)).isEqualTo(timestamp - 1000);
    sessions.deleteById(account.sessionId());
    assertThat(
            activity.renew(
                account.sessionId(), account.account(), 0, AccountRole.CUSTOMER, timestamp))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auth_session_activity WHERE account_id=?",
                Integer.class,
                account.account()))
        .isZero();
  }

  @Test
  void rotatedIdentifierRejectsOldActivityAndReplayWithoutOverwritingNewSession() throws Exception {
    var account = account("CUSTOMER");
    org.springframework.session.Session rotating = sessions.findById(account.sessionId());
    String newIdentifier = rotating.changeSessionId();
    rotating.setAttribute("rotation-fixture", "persist pending rotation");
    assertThat(
            activity.renew(account.sessionId(), account.account(), 0, AccountRole.CUSTOMER, now()))
        .isFalse();
    assertThat(last(account)).isEqualTo(account.originalActivity());
    assertThat(request("GET", "/api/v1/test/eligible", headers(account)).statusCode())
        .isEqualTo(401);
    String newCookie =
        "SESSION="
            + Base64.getEncoder()
                .encodeToString(newIdentifier.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThat(request("GET", "/api/v1/test/eligible", Map.of("Cookie", newCookie)).statusCode())
        .isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM spring_session WHERE session_id=?",
                Integer.class,
                newIdentifier))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM spring_session WHERE session_id=?",
                Integer.class,
                account.sessionId()))
        .isZero();
  }

  @Test
  void zeroRowAdmissionAndStoreFailurePreventHandlerExecution() throws Exception {
    var account = account("CUSTOMER");
    doAnswer(
            call -> {
              jdbc.update(
                  "DELETE FROM auth_session_activity WHERE account_id=?", account.account());
              return call.callRealMethod();
            })
        .when(activity)
        .renew(anyString(), any(), anyLong(), any(), anyLong());
    try {
      assertThat(request("GET", "/api/v1/test/eligible", headers(account)).statusCode())
          .isEqualTo(401);
    } finally {
      reset(activity);
    }
    assertThat(fixture.calls.get()).isZero();
    var unavailable = account("CUSTOMER");
    doThrow(
            new org.springframework.dao.DataAccessResourceFailureException(
                "private admission fixture"))
        .when(activity)
        .renew(anyString(), any(), anyLong(), any(), anyLong());
    try {
      var result = request("GET", "/api/v1/test/eligible", headers(unavailable));
      assertThat(result.statusCode()).isEqualTo(503);
      assertThat(body(result).path("error_code").asInt()).isEqualTo(3002);
      assertThat(result.body()).doesNotContain("private admission fixture");
      assertThat(last(unavailable)).isEqualTo(unavailable.originalActivity());
    } finally {
      reset(activity);
    }
    assertThat(fixture.calls.get()).isZero();
    doThrow(
            new org.springframework.dao.DataAccessResourceFailureException(
                "private validation fixture"))
        .when(activity)
        .inspect(anyString());
    try {
      var result = request("GET", "/api/v1/test/eligible", headers(unavailable));
      assertThat(result.statusCode()).isEqualTo(503);
      assertThat(body(result).path("error_code").asInt()).isEqualTo(3002);
      assertThat(result.body()).doesNotContain("private validation fixture");
      assertThat(fixture.calls.get()).isZero();
    } finally {
      reset(activity);
    }
  }

  /**
   * Test-only non-business MVC/authorization fixture, excluded from component scanning and
   * production artifacts.
   */
  @TestConfiguration(proxyBeanMethods = false)
  @RestController
  static class FixtureConfiguration {
    final JdbcTemplate jdbc;
    final AtomicInteger calls = new AtomicInteger();
    final AtomicLong observed = new AtomicLong();

    FixtureConfiguration(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    @Bean
    @Order(0)
    SecurityFilterChain fixtureSecurity(
        HttpSecurity http,
        AccountSessionValidationFilter validation,
        HttpSessionCsrfTokenRepository csrf,
        HttpSessionSecurityContextRepository contexts,
        ApiErrorWriter errors)
        throws Exception {
      return http.securityMatcher("/api/v1/test/**")
          .securityContext(
              config -> config.requireExplicitSave(true).securityContextRepository(contexts))
          .addFilterBefore(validation, AnonymousAuthenticationFilter.class)
          .csrf(
              config ->
                  config
                      .csrfTokenRepository(csrf)
                      .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler()))
          .authorizeHttpRequests(
              config ->
                  config
                      .requestMatchers(HttpMethod.GET, "/api/v1/test/eligible", "/api/v1/test/poll")
                      .hasRole("CUSTOMER")
                      .requestMatchers(HttpMethod.POST, "/api/v1/test/eligible")
                      .hasRole("CUSTOMER")
                      .requestMatchers(HttpMethod.GET, "/api/v1/test/agent")
                      .hasRole("AGENT")
                      .anyRequest()
                      .denyAll())
          .exceptionHandling(
              config ->
                  config
                      .authenticationEntryPoint(
                          (req, res, ex) ->
                              AccountSessionValidationFilter.unauthorized(res, errors))
                      .accessDeniedHandler(
                          (req, res, ex) ->
                              errors.write(
                                  res,
                                  403,
                                  ApiEnvelope.failure(
                                      ex instanceof CsrfException ? 1010 : 1002,
                                      "fixture-denied",
                                      "Fixture denied",
                                      "Fixture denied"))))
          .requestCache(AbstractHttpConfigurer::disable)
          .formLogin(AbstractHttpConfigurer::disable)
          .httpBasic(AbstractHttpConfigurer::disable)
          .logout(AbstractHttpConfigurer::disable)
          .build();
    }

    @AuthenticatedActivity
    @GetMapping("/api/v1/test/eligible")
    ResponseEntity<ApiEnvelope<?>> eligible(
        @RequestParam(defaultValue = "200") int status,
        jakarta.servlet.http.HttpServletRequest request) {
      calls.incrementAndGet();
      observed.set(
          jdbc.queryForObject(
              "SELECT last_authenticated_activity_at FROM auth_session_activity m JOIN spring_session s ON m.session_primary_id=s.primary_id WHERE s.session_id=?",
              Long.class,
              request.getSession(false).getId()));
      return ResponseEntity.status(status)
          .body(
              status < 400
                  ? ApiEnvelope.success("fixture")
                  : ApiEnvelope.failure(
                      status == 500 ? 3001 : 1004,
                      "fixture-error",
                      "Fixture error",
                      "Fixture error"));
    }

    record RequiredBody(@NotBlank String value) {}

    @AuthenticatedActivity
    @PostMapping("/api/v1/test/eligible")
    ApiEnvelope<?> validation(@Valid @RequestBody RequiredBody input) {
      calls.incrementAndGet();
      return ApiEnvelope.success("fixture");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiEnvelope<?>> validationError() {
      return ResponseEntity.badRequest()
          .body(
              ApiEnvelope.failure(
                  1004, "validation-failed", "Validation failed", "Invalid fixture input"));
    }

    @GetMapping("/api/v1/test/poll")
    ApiEnvelope<?> poll() {
      return ApiEnvelope.success("fixture");
    }

    @AuthenticatedActivity
    @GetMapping("/api/v1/test/agent")
    ApiEnvelope<?> agent() {
      return ApiEnvelope.success("fixture");
    }
  }
}
