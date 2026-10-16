package com.assessment.disputeportal.security.session;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class AuthenticationCleanup {
  private final JdbcTemplate jdbc;

  @Scheduled(cron = "${portal.auth.cleanup-cron:0 * * * * *}")
  public void cleanup() {
    jdbc.update(
        """
            DELETE FROM spring_session s USING auth_session_activity m
            WHERE s.primary_id=m.session_primary_id
              AND m.last_authenticated_activity_at <= floor(extract(epoch FROM clock_timestamp())*1000)::bigint - 1800000
            """);
  }
}
