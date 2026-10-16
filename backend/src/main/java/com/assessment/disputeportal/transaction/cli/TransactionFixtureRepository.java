package com.assessment.disputeportal.transaction.cli;

import com.assessment.disputeportal.transaction.model.TransactionDirection;
import com.assessment.disputeportal.transaction.model.TransactionStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TransactionFixtureRepository {
  private final JdbcTemplate jdbc;

  public void insertIfAbsent(FixtureRow row) {
    jdbc.update(
        "INSERT INTO bank_transactions(id, account_id, reference, merchant, amount, currency, direction, status, occurred_at) "
            + "VALUES (?, ?, ?, ?, ?, 'ZAR', ?, ?, ?) ON CONFLICT DO NOTHING",
        row.id(),
        row.accountId(),
        row.reference(),
        row.merchant(),
        row.amount(),
        row.direction().name(),
        row.status().name(),
        java.sql.Timestamp.from(row.occurredAt()));
  }

  public Optional<FixtureRow> findForUpdate(UUID id) {
    List<FixtureRow> rows =
        jdbc.query(
            "SELECT id, account_id, reference, merchant, amount, direction, status, occurred_at "
                + "FROM bank_transactions WHERE id = ? FOR UPDATE",
            TransactionFixtureRepository::map,
            id);
    return rows.stream().findFirst();
  }

  private static FixtureRow map(ResultSet row, int index) throws SQLException {
    OffsetDateTime occurredAt = row.getObject("occurred_at", OffsetDateTime.class);
    return new FixtureRow(
        row.getObject("id", UUID.class),
        row.getObject("account_id", UUID.class),
        row.getString("reference"),
        row.getString("merchant"),
        row.getBigDecimal("amount"),
        TransactionDirection.valueOf(row.getString("direction")),
        TransactionStatus.valueOf(row.getString("status")),
        occurredAt.toInstant());
  }

  public record FixtureRow(
      UUID id,
      UUID accountId,
      String reference,
      String merchant,
      BigDecimal amount,
      TransactionDirection direction,
      TransactionStatus status,
      Instant occurredAt) {}
}
