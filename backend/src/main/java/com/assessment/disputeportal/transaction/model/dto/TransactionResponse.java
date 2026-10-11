package com.assessment.disputeportal.transaction.model.dto;

import java.util.UUID;

import com.assessment.disputeportal.transaction.model.TransactionDirection;
import com.assessment.disputeportal.transaction.model.TransactionStatus;
import java.time.Instant;

public record TransactionResponse(UUID id, String reference, String merchant, String amount,
        String currency, TransactionDirection direction, TransactionStatus status,
        Instant occurredAt, AccountSummary account, DisputeEligibility dispute) {
    public record AccountSummary(UUID id, String maskedNumber) {}
    public record DisputeEligibility(UUID id, boolean eligible, String ineligibilityReason) {}
}
