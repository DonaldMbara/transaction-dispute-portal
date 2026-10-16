package com.assessment.disputeportal.dispute.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Customer-safe transaction summary embedded in dispute responses. */
public record CustomerTransactionResponse(
    UUID id,
    String merchant,
    String reference,
    BigDecimal amount,
    String currency,
    String direction,
    Instant occurredAt,
    CustomerAccountResponse account) {}
