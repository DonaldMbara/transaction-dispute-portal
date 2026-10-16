package com.assessment.disputeportal.transaction.model;

import com.assessment.disputeportal.common.exception.DomainInvariantException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BankTransaction(
    UUID id,
    UUID accountId,
    String reference,
    String merchant,
    BigDecimal amount,
    String currency,
    TransactionDirection direction,
    TransactionStatus status,
    Instant occurredAt) {
  public BankTransaction {
    Objects.requireNonNull(id);
    Objects.requireNonNull(accountId);
    Objects.requireNonNull(reference);
    Objects.requireNonNull(merchant);
    Objects.requireNonNull(amount);
    Objects.requireNonNull(currency);
    Objects.requireNonNull(direction);
    Objects.requireNonNull(status);
    Objects.requireNonNull(occurredAt);
    if (amount.signum() <= 0 || amount.scale() != 2 || !"ZAR".equals(currency)) {
      throw new DomainInvariantException("Invalid persisted transaction values.");
    }
  }
}
