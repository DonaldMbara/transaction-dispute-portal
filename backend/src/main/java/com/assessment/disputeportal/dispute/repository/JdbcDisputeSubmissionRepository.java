package com.assessment.disputeportal.dispute.repository;

import java.util.Map;
import java.util.UUID;

import com.assessment.disputeportal.dispute.model.Dispute;
import com.assessment.disputeportal.dispute.model.DisputeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcDisputeSubmissionRepository implements DisputeSubmissionRepository {
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public boolean existsForTransaction(UUID transactionId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM disputes WHERE transaction_id = :transactionId)",
                Map.of("transactionId", transactionId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void insert(Dispute dispute) {
        jdbc.update("""
                INSERT INTO disputes (id, transaction_id, reason, description, status, created_at, updated_at, version)
                VALUES (:id, :transactionId, :reason, :description, :status, :createdAt, :updatedAt, :version)
                """, Map.of("id", dispute.id(), "transactionId", dispute.transactionId(),
                        "reason", dispute.reason().name(), "description", dispute.description(),
                        "status", dispute.status().name(), "createdAt", java.sql.Timestamp.from(dispute.createdAt()),
                        "updatedAt", java.sql.Timestamp.from(dispute.updatedAt()), "version", dispute.version()));
    }

    @Override
    public void appendCreated(DisputeEvent event) {
        var params = new MapSqlParameterSource().addValue("id", event.id()).addValue("disputeId", event.disputeId())
                .addValue("actorId", event.actorId()).addValue("version", event.disputeVersion())
                .addValue("eventType", event.eventType().name())
                .addValue("fromStatus", event.fromStatus() == null ? null : event.fromStatus().name())
                .addValue("toStatus", event.toStatus().name()).addValue("previousAgentId", event.previousAgentId())
                .addValue("newAgentId", event.newAgentId()).addValue("occurredAt", java.sql.Timestamp.from(event.createdAt()));
        jdbc.update("""
                INSERT INTO dispute_events
                    (id, dispute_id, actor_id, dispute_version, event_type, from_status, to_status,
                     previous_agent_id, new_agent_id, created_at)
                VALUES (:id, :disputeId, :actorId, :version, :eventType, :fromStatus, :toStatus,
                        :previousAgentId, :newAgentId, :occurredAt)
                """, params);
    }
}
