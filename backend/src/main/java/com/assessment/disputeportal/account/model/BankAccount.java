package com.assessment.disputeportal.account.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Banking account domain summary. Malformed legacy account numbers remain representable for safe
 * disclosure.
 */
public record BankAccount(
    UUID id,
    UUID customerId,
    String accountNumber,
    AccountType accountType,
    BigDecimal balance,
    boolean primary,
    Instant createdAt) {
  public BankAccount {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(customerId, "customerId is required");
    Objects.requireNonNull(accountType, "accountType is required");
    Objects.requireNonNull(balance, "balance is required");
    Objects.requireNonNull(createdAt, "createdAt is required");
  }
}
