package com.assessment.disputeportal.dispute.repository;

import com.assessment.disputeportal.account.mapper.AccountNumberMask;
import com.assessment.disputeportal.account.model.AccountType;
import com.assessment.disputeportal.common.exception.ResourceNotFoundException;
import com.assessment.disputeportal.dispute.model.dto.CustomerAccountSummary;
import com.assessment.disputeportal.dispute.model.dto.CustomerDecision;
import com.assessment.disputeportal.dispute.model.dto.CustomerDisputeDetailDto;
import com.assessment.disputeportal.dispute.model.dto.CustomerDisputeListItemDto;
import com.assessment.disputeportal.dispute.model.dto.CustomerTransactionSummary;
import com.assessment.disputeportal.dispute.model.projection.EventProjection;
import com.assessment.disputeportal.dispute.model.query.DisputeQuery;
import com.assessment.disputeportal.dispute.model.query.StatusPredicate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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

/**
 * Ownership-scoped JDBC read repository for customer dispute queries.
 *
 * <p>SELECT lists are minimal and customer-safe. Sensitive operational fields are not queried, and
 * the full account number is passed directly to the masking function rather than a DTO.
 */
@Repository
@RequiredArgsConstructor
public class JdbcCustomerDisputeReadRepository implements CustomerDisputeReadRepository {

  private final NamedParameterJdbcTemplate jdbc;

  // ---------------------------------------------------------------------------
  // List query
  // ---------------------------------------------------------------------------

  @Override
  public Page<CustomerDisputeListItemDto> findOwned(DisputeQuery query) {
    if (query.accountId() != null
        && !accountIsOwnedByCustomer(query.accountId(), query.authenticatedCustomerId())) {
      throw new ResourceNotFoundException();
    }
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("customerId", query.authenticatedCustomerId());

    StringBuilder where =
        new StringBuilder(
            """
                JOIN bank_transactions t ON t.id = d.transaction_id
                JOIN accounts a          ON a.id = t.account_id
                WHERE a.customer_id = :customerId
                """);

    applyAccountIdClause(query, where, params);
    applyStatusClause(query.statusPredicate(), where, params);
    applyReasonClause(query, where, params);
    applyDateClause(query, where, params);
    applySearchClause(query, where, params);

    // COUNT query — same predicates, no ORDER/LIMIT/OFFSET
    String countSql = "SELECT COUNT(*) FROM disputes d " + where;
    Long total = jdbc.queryForObject(countSql, params, Long.class);
    long totalCount = total != null ? total : 0L;

    if (totalCount == 0) {
      return new PageImpl<>(List.of(), query.pageRequest(), 0L);
    }

    String orderBy = buildOrderBy(query);
    int size = query.pageRequest().getPageSize();
    int offset = (int) query.pageRequest().getOffset();

    String listSql =
        """
                SELECT
                    d.id, d.reason, d.status, d.created_at, d.updated_at, d.resolved_at,
                    t.id              AS transaction_id,
                    t.merchant        AS transaction_merchant,
                    t.reference       AS transaction_reference,
                    t.amount          AS transaction_amount,
                    t.currency        AS transaction_currency,
                    t.direction       AS transaction_direction,
                    t.occurred_at     AS transaction_occurred_at,
                    a.id              AS account_id,
                    a.account_type    AS account_type,
                    a.account_number  AS account_number
                FROM disputes d
                """
            + where
            + orderBy
            + " LIMIT :limit OFFSET :offset";

    params.addValue("limit", size);
    params.addValue("offset", offset);

    List<CustomerDisputeListItemDto> items =
        jdbc.query(listSql, params, (rs, rowNum) -> mapListItem(rs));
    return new PageImpl<>(items, query.pageRequest(), totalCount);
  }

  @Override
  public boolean accountIsOwnedByCustomer(UUID accountId, UUID customerId) {
    Boolean owned =
        jdbc.queryForObject(
            """
                SELECT EXISTS (
                    SELECT 1 FROM accounts
                    WHERE id = :accountId AND customer_id = :customerId
                )
                """,
            Map.of("accountId", accountId, "customerId", customerId),
            Boolean.class);
    return Boolean.TRUE.equals(owned);
  }

  // ---------------------------------------------------------------------------
  // Detail query
  // ---------------------------------------------------------------------------

  @Override
  public Optional<CustomerDisputeDetailDto> findOwnedById(UUID disputeId, UUID customerId) {
    String sql =
        """
                SELECT
                    d.id, d.reason, d.description, d.status,
                    d.customer_resolution_message, d.resolved_at,
                    d.version, d.created_at, d.updated_at,
                    t.id              AS transaction_id,
                    t.merchant        AS transaction_merchant,
                    t.reference       AS transaction_reference,
                    t.amount          AS transaction_amount,
                    t.currency        AS transaction_currency,
                    t.direction       AS transaction_direction,
                    t.occurred_at     AS transaction_occurred_at,
                    a.id              AS account_id,
                    a.account_type    AS account_type,
                    a.account_number  AS account_number
                FROM disputes d
                JOIN bank_transactions t ON t.id = d.transaction_id
                JOIN accounts a          ON a.id = t.account_id
                WHERE d.id = :disputeId
                  AND a.customer_id = :customerId
                """;
    var params = Map.of("disputeId", disputeId, "customerId", customerId);
    List<CustomerDisputeDetailDto> results =
        jdbc.query(sql, params, (rs, rowNum) -> mapDetailRow(rs));
    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
  }

  // ---------------------------------------------------------------------------
  // Event query
  // ---------------------------------------------------------------------------

  @Override
  public List<EventProjection> findEventsByDisputeId(UUID disputeId) {
    String sql =
        """
                SELECT dispute_version, event_type, customer_message, created_at
                FROM dispute_events
                WHERE dispute_id = :disputeId
                ORDER BY dispute_version ASC, created_at ASC
                """;
    return jdbc.query(
        sql,
        Map.of("disputeId", disputeId),
        (rs, rowNum) ->
            new EventProjection(
                rs.getLong("dispute_version"),
                rs.getString("event_type"),
                rs.getString("customer_message"),
                toInstant(rs.getTimestamp("created_at"))));
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  private void applyAccountIdClause(
      DisputeQuery query, StringBuilder where, MapSqlParameterSource params) {
    if (query.accountId() != null) {
      where.append("  AND a.id = :accountId\n");
      params.addValue("accountId", query.accountId());
    }
  }

  private void applyStatusClause(
      StatusPredicate predicate, StringBuilder where, MapSqlParameterSource params) {
    switch (predicate) {
      case StatusPredicate.None ignored -> {
        /* no clause */
      }
      case StatusPredicate.Individual individual -> {
        where.append("  AND d.status = :status\n");
        params.addValue("status", individual.status());
      }
      case StatusPredicate.InList inList -> {
        where.append("  AND d.status IN (:statusList)\n");
        params.addValue("statusList", inList.statuses());
      }
    }
  }

  private void applyReasonClause(
      DisputeQuery query, StringBuilder where, MapSqlParameterSource params) {
    if (query.reason() != null && !query.reason().equals("ALL")) {
      where.append("  AND d.reason = :reason\n");
      params.addValue("reason", query.reason());
    }
  }

  private void applyDateClause(
      DisputeQuery query, StringBuilder where, MapSqlParameterSource params) {
    if (query.fromUtc() != null) {
      where.append("  AND d.created_at >= :fromUtc\n");
      params.addValue("fromUtc", Timestamp.from(query.fromUtc()));
    }
    if (query.toExclusiveUtc() != null) {
      where.append("  AND d.created_at < :toExclusiveUtc\n");
      params.addValue("toExclusiveUtc", Timestamp.from(query.toExclusiveUtc()));
    }
  }

  private void applySearchClause(
      DisputeQuery query, StringBuilder where, MapSqlParameterSource params) {
    if (query.refSearchPattern() != null && query.subPattern() != null) {
      where.append(
          """
                    AND (d.id::text ILIKE :refPattern
                      OR d.description ILIKE :subPattern
                      OR t.merchant ILIKE :subPattern
                      OR t.reference ILIKE :subPattern)
                    """);
      params.addValue("refPattern", query.refSearchPattern());
      params.addValue("subPattern", query.subPattern());
    }
  }

  private static final Map<String, String> SORT_FIELD_MAP =
      Map.of(
          "createdAt", "d.created_at",
          "updatedAt", "d.updated_at",
          "status", "d.status");

  private String buildOrderBy(DisputeQuery query) {
    String direction = "ASC".equalsIgnoreCase(query.sortDirection()) ? "ASC" : "DESC";
    String field = SORT_FIELD_MAP.getOrDefault(query.sortField(), "d.created_at");
    return " ORDER BY " + field + " " + direction + ", d.id " + direction;
  }

  private CustomerDisputeListItemDto mapListItem(ResultSet rs) throws SQLException {
    String rawAccountNumber = rs.getString("account_number");
    String accountType = rs.getString("account_type");

    CustomerAccountSummary account =
        new CustomerAccountSummary(
            UUID.fromString(rs.getString("account_id")),
            displayNameFor(accountType),
            AccountNumberMask.mask(rawAccountNumber));

    CustomerTransactionSummary transaction =
        new CustomerTransactionSummary(
            UUID.fromString(rs.getString("transaction_id")),
            rs.getString("transaction_merchant"),
            rs.getString("transaction_reference"),
            rs.getBigDecimal("transaction_amount"),
            rs.getString("transaction_currency"),
            rs.getString("transaction_direction"),
            toInstant(rs.getTimestamp("transaction_occurred_at")),
            account);

    return new CustomerDisputeListItemDto(
        UUID.fromString(rs.getString("id")),
        transaction,
        rs.getString("reason"),
        rs.getString("status"),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("updated_at")),
        toInstant(rs.getTimestamp("resolved_at")));
  }

  private CustomerDisputeDetailDto mapDetailRow(ResultSet rs) throws SQLException {
    String rawAccountNumber = rs.getString("account_number");
    String accountType = rs.getString("account_type");

    CustomerAccountSummary account =
        new CustomerAccountSummary(
            UUID.fromString(rs.getString("account_id")),
            displayNameFor(accountType),
            AccountNumberMask.mask(rawAccountNumber));

    CustomerTransactionSummary transaction =
        new CustomerTransactionSummary(
            UUID.fromString(rs.getString("transaction_id")),
            rs.getString("transaction_merchant"),
            rs.getString("transaction_reference"),
            rs.getBigDecimal("transaction_amount"),
            rs.getString("transaction_currency"),
            rs.getString("transaction_direction"),
            toInstant(rs.getTimestamp("transaction_occurred_at")),
            account);

    String status = rs.getString("status");
    CustomerDecision decision = buildDecision(status, rs.getString("customer_resolution_message"));

    return new CustomerDisputeDetailDto(
        UUID.fromString(rs.getString("id")),
        transaction,
        rs.getString("reason"),
        status,
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("updated_at")),
        toInstant(rs.getTimestamp("resolved_at")),
        rs.getString("description"),
        decision,
        new ArrayList<>() /* timeline populated by service layer */);
  }

  private static String displayNameFor(String accountType) {
    try {
      return AccountType.valueOf(accountType).displayName();
    } catch (IllegalArgumentException ignored) {
      return accountType;
    }
  }

  private static CustomerDecision buildDecision(String status, String customerResolutionMessage) {
    if ("ACCEPTED".equals(status) || "REJECTED".equals(status)) {
      return new CustomerDecision(status, customerResolutionMessage);
    }
    return null;
  }

  private static Instant toInstant(Timestamp ts) {
    return ts != null ? ts.toInstant() : null;
  }
}
