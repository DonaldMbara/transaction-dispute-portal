package com.assessment.disputeportal.transaction.port;

import java.util.Optional;
import java.util.UUID;

/** Transaction-owned, ownership-scoped access required by dispute submission. */
public interface DisputeTransactionPort {
  Optional<SubmissionTransaction> lockOwned(UUID customerId, UUID transactionId);
}
