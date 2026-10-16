package com.assessment.disputeportal.transaction.repository;

import com.assessment.disputeportal.transaction.model.TransactionStatus;
import com.assessment.disputeportal.transaction.port.DisputeTransactionPort;
import com.assessment.disputeportal.transaction.port.SubmissionTransaction;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcDisputeTransactionAdapter implements DisputeTransactionPort {
  private final NamedParameterJdbcTemplate jdbc;

  @Override
  public Optional<SubmissionTransaction> lockOwned(UUID customerId, UUID transactionId) {
    var rows =
        jdbc.query(
            """
                SELECT t.id, t.reference, t.merchant, t.amount, t.currency, t.direction, t.status, t.occurred_at
                FROM bank_transactions t
                JOIN accounts a ON a.id = t.account_id
                WHERE t.id = :transactionId AND a.customer_id = :customerId
                FOR UPDATE OF t
                """,
            Map.of("transactionId", transactionId, "customerId", customerId),
            (rs, n) ->
                new SubmissionTransaction(
                    rs.getObject("id", UUID.class),
                    rs.getString("reference"),
                    rs.getString("merchant"),
                    rs.getBigDecimal("amount"),
                    rs.getString("currency").trim(),
                    rs.getString("direction"),
                    TransactionStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("occurred_at").toInstant()));
    return rows.stream().findFirst();
  }
}
