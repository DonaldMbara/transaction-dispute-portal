package com.assessment.disputeportal.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.assessment.disputeportal.auth.port.AuthenticationCredentialsLookup;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Own isolated container/context; deterministic public-boundary and PostgreSQL fault injection. */
class AuthenticationStoreFailureTest extends AuthenticationApiIntegrationTest {
  @MockitoSpyBean AuthenticationCredentialsLookup credentials;

  void fault(String table, String event, String condition) {
    jdbc.execute(
        "CREATE OR REPLACE FUNCTION auth_test_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'isolated private store failure'; END $$");
    jdbc.execute(
        "CREATE TRIGGER auth_test_fault BEFORE "
            + event
            + " ON "
            + table
            + " FOR EACH ROW "
            + condition
            + " EXECUTE FUNCTION auth_test_fault()");
  }

  void unfault(String table) {
    jdbc.execute("DROP TRIGGER IF EXISTS auth_test_fault ON " + table);
    jdbc.execute("DROP FUNCTION IF EXISTS auth_test_fault()");
  }

  @Test
  void accountAndSessionReadFailuresNeverBecomeCredentialFailures() throws Exception {
    account("CUSTOMER", true);
    var b = request("GET", "/api/v1/auth/csrf", Map.of());
    doThrow(new org.springframework.dao.DataAccessResourceFailureException("private account fault"))
        .when(credentials)
        .findByEmail(anyString());
    var failed = login(cookie(b), body(b).at("/data/csrfToken").asText());
    assertThat(failed.statusCode()).isEqualTo(503);
    assertThat(body(failed).path("error_code").asInt()).isEqualTo(3002);
    org.mockito.Mockito.reset(credentials);
    doThrow(new org.springframework.dao.DataAccessResourceFailureException("private session fault"))
        .when(sessions)
        .findById(anyString());
    var read = request("GET", "/api/v1/auth/me", Map.of("Cookie", cookie(b)));
    assertThat(read.statusCode()).isEqualTo(503);
    assertThat(read.body()).doesNotContain("private session fault");
    org.mockito.Mockito.reset(sessions);
  }

  @Test
  void contextRotationAndActivationWriteFaultsWithholdUsableAuthentication() throws Exception {
    for (String stage : List.of("context", "rotation", "activation")) {
      super.reset();
      account("CUSTOMER", true);
      var b = request("GET", "/api/v1/auth/csrf", Map.of());
      var old = cookie(b);
      String table =
          stage.equals("context")
              ? "spring_session_attributes"
              : stage.equals("rotation") ? "spring_session" : "auth_session_activity";
      fault(
          table,
          stage.equals("rotation") ? "UPDATE" : "INSERT",
          stage.equals("rotation")
              ? "WHEN (NEW.session_id <> OLD.session_id)"
              : stage.equals("context")
                  ? "WHEN (NEW.attribute_name='SPRING_SECURITY_CONTEXT')"
                  : "");
      try {
        var result = login(old, body(b).at("/data/csrfToken").asText());
        assertThat(result.statusCode()).as(stage).isEqualTo(503);
        assertThat(body(result).path("error_code").asInt()).isEqualTo(3002);
        assertThat(result.body()).doesNotContain("isolated private store failure");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_session_activity", Integer.class))
            .isZero();
        assertThat(request("GET", "/api/v1/auth/me", Map.of("Cookie", old)).statusCode())
            .isEqualTo(401);
      } finally {
        unfault(table);
      }
    }
  }

  @Test
  void accessAdmissionAndLogoutFaultsAreUnavailableBeforeSuccess() throws Exception {
    for (String stage : List.of("access", "admission", "logout")) {
      super.reset();
      account("CUSTOMER", true);
      var b = request("GET", "/api/v1/auth/csrf", Map.of());
      var r = login(cookie(b), body(b).at("/data/csrfToken").asText());
      var c = cookie(r);
      var fresh = request("GET", "/api/v1/auth/csrf", Map.of("Cookie", c));
      String table = stage.equals("admission") ? "auth_session_activity" : "spring_session";
      fault(table, stage.equals("logout") ? "DELETE" : "UPDATE", "");
      try {
        var failed =
            stage.equals("logout")
                ? post("/api/v1/auth/logout", c, body(fresh).at("/data/csrfToken").asText(), "")
                : request("GET", "/api/v1/auth/me", Map.of("Cookie", c));
        assertThat(failed.statusCode()).as(stage).isEqualTo(503);
        assertThat(body(failed).path("error_code").asInt()).isEqualTo(3002);
        assertThat(failed.body()).doesNotContain("isolated private store failure");
      } finally {
        unfault(table);
      }
      assertThat(request("GET", "/api/v1/auth/me", Map.of("Cookie", c)).statusCode())
          .isEqualTo(200);
    }
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void frameworkPreCommitSaveFailureIsMappedByOuterFilter() throws Exception {
    account("CUSTOMER", true);
    var b = request("GET", "/api/v1/auth/csrf", Map.of());
    org.springframework.session.SessionRepository repository = sessions;
    var attempts = new java.util.concurrent.atomic.AtomicInteger();
    doAnswer(
            call -> {
              boolean commit =
                  Arrays.stream(Thread.currentThread().getStackTrace())
                      .anyMatch(
                          frame -> frame.getClassName().contains("OnCommittedResponseWrapper"));
              if (commit) {
                attempts.incrementAndGet();
                throw new org.springframework.dao.DataAccessResourceFailureException(
                    "private precommit fault");
              }
              return call.callRealMethod();
            })
        .when(repository)
        .save(any(org.springframework.session.Session.class));
    try {
      var result = login(cookie(b), body(b).at("/data/csrfToken").asText());
      assertThat(attempts.get()).isPositive();
      assertThat(result.statusCode()).isEqualTo(503);
      assertThat(body(result).path("error_code").asInt()).isEqualTo(3002);
      assertThat(result.headers().allValues("set-cookie")).isEmpty();
      assertThat(result.body()).doesNotContain("private precommit fault");
    } finally {
      org.mockito.Mockito.reset(sessions);
    }
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void postCommitFinalizationFailureIsReportedWithoutRetroactiveStatusClaim() throws Exception {
    account("CUSTOMER", true);
    var b = request("GET", "/api/v1/auth/csrf", Map.of());
    var logger =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(
                com.assessment.disputeportal.security.web.AvailabilityFilter.class);
    var capture =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    capture.start();
    logger.addAppender(capture);
    org.springframework.session.SessionRepository repository = sessions;
    var attempts = new java.util.concurrent.atomic.AtomicInteger();
    doAnswer(
            call -> {
              var stack = Arrays.asList(Thread.currentThread().getStackTrace());
              boolean repositoryFilter =
                  stack.stream()
                      .anyMatch(
                          frame ->
                              frame
                                  .getClassName()
                                  .equals(
                                      "org.springframework.session.web.http.SessionRepositoryFilter"));
              boolean commitWrapper =
                  stack.stream()
                      .anyMatch(
                          frame -> frame.getClassName().contains("OnCommittedResponseWrapper"));
              if (repositoryFilter && !commitWrapper) {
                attempts.incrementAndGet();
                throw new org.springframework.dao.DataAccessResourceFailureException(
                    "private finalization fault");
              }
              return call.callRealMethod();
            })
        .when(repository)
        .save(any(org.springframework.session.Session.class));
    try {
      var req =
          java.net.http.HttpRequest.newBuilder(
                  java.net.URI.create("http://localhost:" + port + "/api/v1/auth/login"))
              .timeout(java.time.Duration.ofSeconds(10))
              .header("Cookie", cookie(b))
              .header("X-CSRF-TOKEN", body(b).at("/data/csrfToken").asText())
              .header("Content-Type", "application/json")
              .POST(
                  java.net.http.HttpRequest.BodyPublishers.ofString(
                      json.writeValueAsString(
                          Map.of("email", "login@example.test", "password", PASSWORD))))
              .build();
      var result = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
      assertThat(result.statusCode())
          .isEqualTo(
              200); // Headers were physically committed; body completion cannot be confirmed.
      try (var stream = result.body()) {
        assertThatThrownBy(stream::readAllBytes).isInstanceOf(java.io.IOException.class);
      }
      assertThat(attempts.get()).isPositive();
      assertThat(
              capture.list.stream()
                  .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                  .toList())
          .anyMatch(value -> value.contains("committed=true"));
    } finally {
      org.mockito.Mockito.reset(sessions);
      logger.detachAppender(capture);
      capture.stop();
    }
  }
}
