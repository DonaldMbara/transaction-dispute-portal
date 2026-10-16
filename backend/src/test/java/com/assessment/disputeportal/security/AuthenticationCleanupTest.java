package com.assessment.disputeportal.security;

import static org.assertj.core.api.Assertions.*;

import com.assessment.disputeportal.security.session.AuthenticationCleanup;
import com.assessment.disputeportal.security.session.SessionTimeoutProperties;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuthenticationCleanupTest extends SessionIntegrationSupport {
  @Autowired AuthenticationCleanup cleanup;

  @Test
  void expiredParentsCascadeButActiveAndAnonymousSurviveIdempotentConcurrentCleanup()
      throws Exception {
    jdbc.update("DELETE FROM spring_session");
    jdbc.update("DELETE FROM app_users");
    var id = java.util.UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app_users(id,email,password_hash,role,enabled,created_at) VALUES(?,?,?,'CUSTOMER',true,now())",
        id,
        "cleanup@example.test",
        "{pbkdf2-sha256-v1}" + "0".repeat(96));
    org.springframework.session.Session expired = sessions.createSession(),
        active = sessions.createSession(),
        anonymous = sessions.createSession();
    expired.setAttribute("fixture", "expired");
    active.setAttribute("fixture", "active");
    anonymous.setAttribute("fixture", "anonymous");
    long now =
        jdbc.queryForObject(
            "SELECT floor(extract(epoch FROM clock_timestamp())*1000)::bigint", Long.class);
    for (var session : java.util.List.of(expired, active))
      jdbc.update(
          "INSERT INTO auth_session_activity SELECT primary_id,?,0,? FROM spring_session WHERE session_id=?",
          id,
          session == expired ? now - 1800000 : now,
          session.getId());
    try (var pool = Executors.newFixedThreadPool(2)) {
      var barrier = new CyclicBarrier(2);
      Callable<Void> task =
          () -> {
            barrier.await(5, TimeUnit.SECONDS);
            new AuthenticationCleanup(jdbc, new SessionTimeoutProperties(Duration.ofMinutes(30)))
                .cleanup();
            return null;
          };
      var first = pool.submit(task);
      var second = pool.submit(task);
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    }
    cleanup.cleanup();
    assertThat(sessions.findById(expired.getId())).isNull();
    assertThat(sessions.findById(active.getId())).isNotNull();
    var restarted =
        new org.springframework.session.jdbc.JdbcIndexedSessionRepository(
            jdbc,
            new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                    jdbc.getDataSource())));
    restarted.setCleanupCron("-");
    org.springframework.session.Session restored = restarted.findById(active.getId());
    assertThat(restored).isNotNull();
    assertThat((String) restored.getAttribute("fixture")).isEqualTo("active");
    assertThat(sessions.findById(anonymous.getId())).isNotNull();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_session_activity", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session_attributes", Integer.class))
        .isEqualTo(2);
    assertThat(
            cleanup
                .getClass()
                .getMethod("cleanup")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class)
                .cron())
        .contains("portal.auth.cleanup-cron");
  }
}
