package com.assessment.disputeportal.transaction.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.assessment.disputeportal.transaction.model.TransactionStatus;

/** Transaction-owned, ownership-scoped access required by dispute submission. */
public interface DisputeTransactionPort {
    Optional<SubmissionTransaction> lockOwned(UUID customerId, UUID transactionId);

    record SubmissionTransaction(UUID id, String reference, String merchant, BigDecimal amount,
            String currency, String direction, TransactionStatus status, Instant occurredAt) {}
}
