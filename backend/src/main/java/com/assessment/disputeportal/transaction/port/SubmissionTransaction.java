package com.assessment.disputeportal.transaction.port;

import com.assessment.disputeportal.transaction.model.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubmissionTransaction(
    UUID id,
    String reference,
    String merchant,
    BigDecimal amount,
    String currency,
    String direction,
    TransactionStatus status,
    Instant occurredAt) {}
