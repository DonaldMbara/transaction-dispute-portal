package com.assessment.disputeportal.dispute.repository;

import com.assessment.disputeportal.account.mapper.AccountNumberMask;
import com.assessment.disputeportal.account.model.AccountType;
import com.assessment.disputeportal.dispute.model.dto.AgentDisputeDetailResponse;
import com.assessment.disputeportal.dispute.model.dto.AgentDisputeEventResponse;
import com.assessment.disputeportal.dispute.model.dto.AgentDisputeListItemResponse;
import com.assessment.disputeportal.dispute.model.dto.AgentIdentityResponse;
import com.assessment.disputeportal.dispute.model.query.AgentDisputeQuery;
import com.assessment.disputeportal.dispute.model.query.DisputeQuery;
import com.assessment.disputeportal.dispute.model.query.StatusPredicate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcAgentDisputeReadRepository implements AgentDisputeReadRepository {
  private static final Map<String, String> SORT_FIELDS =
      Map.of("createdAt", "d.created_at", "updatedAt", "d.updated_at", "status", "d.status");
  private final NamedParameterJdbcTemplate jdbc;

  @Override
  public Page<AgentDisputeListItemResponse> find(AgentDisputeQuery query) {
    DisputeQuery filters = query.filters();
    MapSqlParameterSource params = new MapSqlParameterSource("agentId", query.agentId());
    StringBuilder fromWhere =
        new StringBuilder(
            """
                FROM disputes d
                JOIN bank_transactions t ON t.id = d.transaction_id
                LEFT JOIN app_users au ON au.id = d.assigned_agent_id
                WHERE 1 = 1
                """);
    switch (query.queue()) {
      case UNASSIGNED ->
          fromWhere.append(" AND d.status = 'OPEN' AND d.assigned_agent_id IS NULL\n");
      case MINE -> fromWhere.append(" AND d.assigned_agent_id = :agentId\n");
      case ALL -> {}
    }
    applyFilters(filters, fromWhere, params);
    long total =
        Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) " + fromWhere, params, Long.class))
            .orElse(0L);
    if (total == 0) return new PageImpl<>(List.of(), filters.pageRequest(), 0);
    String direction = "ASC".equalsIgnoreCase(filters.sortDirection()) ? "ASC" : "DESC";
    String sort = SORT_FIELDS.getOrDefault(filters.sortField(), "d.created_at");
    params.addValue("limit", filters.pageRequest().getPageSize());
    params.addValue("offset", filters.pageRequest().getOffset());
    List<AgentDisputeListItemResponse> items =
        jdbc.query(
            """
                SELECT d.id, d.reason, d.status, d.created_at, d.updated_at, d.resolved_at, d.version,
                       t.merchant, t.amount, t.currency, t.direction, au.id AS agent_id, au.email AS agent_email
                """
                + fromWhere
                + " ORDER BY "
                + sort
                + " "
                + direction
                + ", d.id "
                + direction
                + " LIMIT :limit OFFSET :offset",
            params,
            this::mapListItem);
    return new PageImpl<>(items, filters.pageRequest(), total);
  }

  @Override
  public Optional<AgentDisputeDetailResponse> findById(UUID disputeId) {
    List<AgentDisputeDetailResponse> rows =
        jdbc.query(
            """
                SELECT d.id, d.transaction_id, d.reason, d.description, d.status,
                       d.assigned_agent_id, d.assigned_at, d.internal_resolution_notes,
                       d.customer_resolution_message, d.created_at, d.updated_at, d.resolved_at, d.version,
                       t.merchant, t.amount, t.currency, t.direction, t.occurred_at,
                       t.reference AS transaction_reference,
                       a.id AS account_id, a.account_type, a.account_number,
                       au.email AS agent_email
                FROM disputes d
                JOIN bank_transactions t ON t.id = d.transaction_id
                JOIN accounts a ON a.id = t.account_id
                LEFT JOIN app_users au ON au.id = d.assigned_agent_id
                WHERE d.id = :id
                """,
            Map.of("id", disputeId),
            (rs, row) -> mapDetail(rs, List.of()));
    if (rows.isEmpty()) return Optional.empty();
    List<AgentDisputeEventResponse> events =
        jdbc.query(
            """
                SELECT id, dispute_version, event_type, actor_id, from_status, to_status,
                       previous_agent_id, new_agent_id, internal_notes, customer_message, created_at
                FROM dispute_events WHERE dispute_id = :id
                ORDER BY dispute_version ASC, created_at ASC
                """,
            Map.of("id", disputeId),
            (rs, row) -> mapEvent(rs));
    AgentDisputeDetailResponse row = rows.get(0);
    return Optional.of(
        new AgentDisputeDetailResponse(
            row.id(),
            row.reference(),
            row.transactionId(),
            row.merchant(),
            row.amount(),
            row.currency(),
            row.direction(),
            row.occurredAt(),
            row.transactionReference(),
            row.accountId(),
            row.accountDisplayName(),
            row.maskedAccountNumber(),
            row.reason(),
            row.status(),
            row.description(),
            row.createdAt(),
            row.updatedAt(),
            row.assignedAt(),
            row.resolvedAt(),
            row.assignedAgent(),
            row.internalResolutionNotes(),
            row.customerResolutionMessage(),
            row.version(),
            events));
  }

  private void applyFilters(DisputeQuery query, StringBuilder sql, MapSqlParameterSource params) {
    switch (query.statusPredicate()) {
      case StatusPredicate.None ignored -> {}
      case StatusPredicate.Individual individual -> {
        sql.append(" AND d.status = :status\n");
        params.addValue("status", individual.status());
      }
      case StatusPredicate.InList list -> {
        sql.append(" AND d.status IN (:statuses)\n");
        params.addValue("statuses", list.statuses());
      }
    }
    if (query.reason() != null && !"ALL".equals(query.reason())) {
      sql.append(" AND d.reason = :reason\n");
      params.addValue("reason", query.reason());
    }
    if (query.fromUtc() != null) {
      sql.append(" AND d.created_at >= :fromUtc\n");
      params.addValue("fromUtc", Timestamp.from(query.fromUtc()));
    }
    if (query.toExclusiveUtc() != null) {
      sql.append(" AND d.created_at < :toUtc\n");
      params.addValue("toUtc", Timestamp.from(query.toExclusiveUtc()));
    }
    if (query.refSearchPattern() != null) {
      sql.append(
          " AND (d.id::text ILIKE :refPattern OR d.description ILIKE :subPattern OR t.merchant ILIKE :subPattern OR t.reference ILIKE :subPattern)\n");
      params.addValue("refPattern", query.refSearchPattern());
      params.addValue("subPattern", query.subPattern());
    }
  }

  private AgentDisputeListItemResponse mapListItem(ResultSet rs, int row) throws SQLException {
    UUID id = rs.getObject("id", UUID.class);
    UUID agentId = rs.getObject("agent_id", UUID.class);
    return new AgentDisputeListItemResponse(
        id,
        disputeReference(id),
        rs.getString("merchant"),
        rs.getBigDecimal("amount").setScale(2).toPlainString(),
        rs.getString("currency"),
        rs.getString("direction"),
        rs.getString("reason"),
        rs.getString("status"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"),
        instant(rs, "resolved_at"),
        agentId == null ? null : new AgentIdentityResponse(agentId, rs.getString("agent_email")),
        rs.getLong("version"));
  }

  private AgentDisputeDetailResponse mapDetail(ResultSet rs, List<AgentDisputeEventResponse> events)
      throws SQLException {
    UUID id = rs.getObject("id", UUID.class);
    UUID agentId = rs.getObject("assigned_agent_id", UUID.class);
    String type = rs.getString("account_type");
    String displayName;
    try {
      displayName = AccountType.valueOf(type).displayName();
    } catch (IllegalArgumentException ignored) {
      displayName = type;
    }
    return new AgentDisputeDetailResponse(
        id,
        disputeReference(id),
        rs.getObject("transaction_id", UUID.class),
        rs.getString("merchant"),
        rs.getBigDecimal("amount").setScale(2).toPlainString(),
        rs.getString("currency"),
        rs.getString("direction"),
        instant(rs, "occurred_at"),
        rs.getString("transaction_reference"),
        rs.getObject("account_id", UUID.class),
        displayName,
        AccountNumberMask.mask(rs.getString("account_number")),
        rs.getString("reason"),
        rs.getString("status"),
        rs.getString("description"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"),
        instant(rs, "assigned_at"),
        instant(rs, "resolved_at"),
        agentId == null ? null : new AgentIdentityResponse(agentId, rs.getString("agent_email")),
        rs.getString("internal_resolution_notes"),
        rs.getString("customer_resolution_message"),
        rs.getLong("version"),
        events);
  }

  private AgentDisputeEventResponse mapEvent(ResultSet rs) throws SQLException {
    return new AgentDisputeEventResponse(
        rs.getObject("id", UUID.class),
        rs.getLong("dispute_version"),
        rs.getString("event_type"),
        rs.getObject("actor_id", UUID.class),
        rs.getString("from_status"),
        rs.getString("to_status"),
        rs.getObject("previous_agent_id", UUID.class),
        rs.getObject("new_agent_id", UUID.class),
        rs.getString("internal_notes"),
        rs.getString("customer_message"),
        instant(rs, "created_at"));
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private static String disputeReference(UUID id) {
    return "DSP-"
        + id.toString().replace("-", "").substring(0, 8).toUpperCase(java.util.Locale.ROOT);
  }
}
