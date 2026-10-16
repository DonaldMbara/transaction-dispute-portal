package com.assessment.disputeportal.dispute.repository;

import com.assessment.disputeportal.dispute.model.Dispute;
import com.assessment.disputeportal.dispute.model.DisputeEvent;
import com.assessment.disputeportal.dispute.model.DisputeReason;
import com.assessment.disputeportal.dispute.model.DisputeStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcAgentDisputeWriteRepository implements AgentDisputeWriteRepository {
  private final NamedParameterJdbcTemplate jdbc;

  @Override
  public Optional<Dispute> lockById(UUID id) {
    List<Dispute> rows =
        jdbc.query(
            """
                SELECT id, transaction_id, reason, description, status, created_at, updated_at, version,
                       assigned_agent_id, assigned_at, resolved_at, internal_resolution_notes, customer_resolution_message
                FROM disputes WHERE id = :id FOR UPDATE
                """,
            Map.of("id", id),
            this::map);
    return rows.stream().findFirst();
  }

  @Override
  public boolean update(Dispute before, Dispute after) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", before.id())
            .addValue("expectedVersion", before.version())
            .addValue("expectedStatus", before.status().name())
            .addValue("expectedAgent", before.assignedAgentId())
            .addValue("status", after.status().name())
            .addValue("agent", after.assignedAgentId())
            .addValue("assignedAt", timestamp(after.assignedAt()))
            .addValue("resolvedAt", timestamp(after.resolvedAt()))
            .addValue("internalNotes", after.internalResolutionNotes())
            .addValue("customerMessage", after.customerResolutionMessage())
            .addValue("updatedAt", Timestamp.from(after.updatedAt()))
            .addValue("version", after.version());
    return jdbc.update(
            """
                UPDATE disputes SET status = :status, assigned_agent_id = :agent, assigned_at = :assignedAt,
                    resolved_at = :resolvedAt, internal_resolution_notes = :internalNotes,
                    customer_resolution_message = :customerMessage, updated_at = :updatedAt, version = :version
                WHERE id = :id AND version = :expectedVersion AND status = :expectedStatus
                  AND assigned_agent_id IS NOT DISTINCT FROM :expectedAgent
                """,
            params)
        == 1;
  }

  @Override
  public void append(DisputeEvent event) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", event.id())
            .addValue("disputeId", event.disputeId())
            .addValue("actorId", event.actorId())
            .addValue("version", event.disputeVersion())
            .addValue("type", event.eventType().name())
            .addValue("fromStatus", event.fromStatus() == null ? null : event.fromStatus().name())
            .addValue("toStatus", event.toStatus().name())
            .addValue("previousAgent", event.previousAgentId())
            .addValue("newAgent", event.newAgentId())
            .addValue("internalNotes", event.internalNotes())
            .addValue("customerMessage", event.customerMessage())
            .addValue("createdAt", Timestamp.from(event.createdAt()));
    jdbc.update(
        """
                INSERT INTO dispute_events (id, dispute_id, actor_id, dispute_version, event_type,
                    from_status, to_status, previous_agent_id, new_agent_id, internal_notes, customer_message, created_at)
                VALUES (:id, :disputeId, :actorId, :version, :type, :fromStatus, :toStatus,
                    :previousAgent, :newAgent, :internalNotes, :customerMessage, :createdAt)
                """,
        params);
  }

  private Dispute map(ResultSet rs, int row) throws SQLException {
    return new Dispute(
        rs.getObject("id", UUID.class),
        rs.getObject("transaction_id", UUID.class),
        DisputeReason.valueOf(rs.getString("reason")),
        rs.getString("description"),
        DisputeStatus.valueOf(rs.getString("status")),
        instant(rs, "created_at"),
        instant(rs, "updated_at"),
        rs.getLong("version"),
        rs.getObject("assigned_agent_id", UUID.class),
        instant(rs, "assigned_at"),
        instant(rs, "resolved_at"),
        rs.getString("internal_resolution_notes"),
        rs.getString("customer_resolution_message"));
  }

  private static Instant instant(ResultSet rs, String name) throws SQLException {
    Timestamp value = rs.getTimestamp(name);
    return value == null ? null : value.toInstant();
  }

  private static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }
}
