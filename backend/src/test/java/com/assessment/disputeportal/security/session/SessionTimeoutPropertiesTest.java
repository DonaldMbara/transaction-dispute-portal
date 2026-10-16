package com.assessment.disputeportal.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.assessment.disputeportal.auth.model.AccountIdentity;
import com.assessment.disputeportal.auth.model.AccountRole;
import com.assessment.disputeportal.common.exception.InvalidSessionAccessTimeException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SessionTimeoutPropertiesTest {
  private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(7);

  @Test
  void configuredIdleTimeoutDrivesRenewalAndCleanupSql() {
    var jdbc = mock(JdbcTemplate.class);
    var properties = new SessionTimeoutProperties(IDLE_TIMEOUT);
    var account =
        new AccountIdentity(UUID.randomUUID(), "a@example.test", AccountRole.CUSTOMER, true, 1);
    var store = new JdbcSessionActivityStore(jdbc, properties);
    var cleanup = new AuthenticationCleanup(jdbc, properties);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    assertThat(store.renew("session", account.id(), 1, AccountRole.CUSTOMER, 10)).isTrue();
    cleanup.cleanup();

    verify(jdbc)
        .update(
            contains("UPDATE auth_session_activity"),
            eq(10L),
            eq("session"),
            eq(account.id()),
            eq(1L),
            eq("CUSTOMER"),
            eq(420_000L));
    verify(jdbc).update(contains("DELETE FROM spring_session"), eq(420_000L));
  }

  @Test
  void negativeFrameworkAccessTimeRaisesTheExplicitHandledException() {
    var store =
        new JdbcSessionActivityStore(
            mock(JdbcTemplate.class), new SessionTimeoutProperties(IDLE_TIMEOUT));

    assertThatThrownBy(() -> store.renew("session", UUID.randomUUID(), 1, AccountRole.CUSTOMER, -1))
        .isInstanceOf(InvalidSessionAccessTimeException.class);
  }
}
