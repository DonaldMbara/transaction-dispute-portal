package com.assessment.disputeportal.security.activity;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.assessment.disputeportal.auth.model.AccountIdentity;
import com.assessment.disputeportal.auth.model.AccountRole;
import com.assessment.disputeportal.common.exception.DomainInvariantException;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcSessionActivityStore implements SessionActivityStore {

    private final JdbcTemplate jdbc;

    @Override
    public Optional<Snapshot> inspect(String sessionId) {
        return jdbc.query("""
                WITH moment AS MATERIALIZED (SELECT floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint AS now)
                SELECT m.account_id, m.auth_version, u.auth_version AS current_version, u.enabled, u.role,
                       m.last_authenticated_activity_at, moment.now
                FROM auth_session_activity m JOIN spring_session s ON s.primary_id=m.session_primary_id
                JOIN app_users u ON u.id=m.account_id CROSS JOIN moment
                WHERE s.session_id=? AND s.expiry_time > moment.now
                """,
                (row, index) -> new Snapshot(
                        row.getObject("account_id", UUID.class),
                        row.getLong("auth_version"),
                        row.getLong("current_version"),
                        row.getBoolean("enabled"),
                        row.getString("role"),
                        row.getLong("last_authenticated_activity_at"),
                        row.getLong("now")),
                sessionId)
                .stream().findFirst();
    }

                @Transactional(propagation = Propagation.REQUIRES_NEW)
                @Override
                public boolean activate(String sessionId, AccountIdentity account, long accessTime) {
              if (accessTime < 0) throw new DomainInvariantException("Invalid framework session access time.");
              var current = jdbc.query(
                "SELECT enabled,auth_version,role FROM app_users WHERE id=? FOR UPDATE",
                (row, index) -> row.getBoolean("enabled")
                  && row.getLong("auth_version") == account.authVersion()
                  && row.getString("role").equals(account.role().name()),
                account.id());
              if (current.isEmpty() || !current.getFirst()) return false;

              jdbc.update(
                "DELETE FROM auth_session_activity WHERE session_primary_id IN (SELECT primary_id FROM spring_session WHERE session_id=?)",
                sessionId);
              return jdbc.update("""
                INSERT INTO auth_session_activity(session_primary_id,account_id,auth_version,last_authenticated_activity_at)
                SELECT primary_id,?,?,? FROM spring_session WHERE session_id=?
                """, account.id(), account.authVersion(), accessTime, sessionId) == 1;
                }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public boolean renew(String sessionId, UUID accountId, long version, AccountRole role, long accessTime) {
        if (accessTime < 0) throw new DomainInvariantException("Invalid framework session access time.");
        return jdbc.update("""
                WITH moment AS MATERIALIZED (SELECT floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint AS now)
                UPDATE auth_session_activity m
                SET last_authenticated_activity_at=GREATEST(m.last_authenticated_activity_at, ?)
                FROM spring_session s, app_users u, moment
                WHERE s.primary_id=m.session_primary_id AND s.session_id=?
                  AND m.account_id=? AND m.auth_version=?
                  AND u.id=m.account_id AND u.enabled AND u.role=? AND u.auth_version=m.auth_version
                  AND m.last_authenticated_activity_at > moment.now - 1800000
                  AND s.expiry_time > moment.now
                """, accessTime, sessionId, accountId, version, role.name()) == 1;
    }
}
