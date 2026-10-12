package com.assessment.disputeportal.dispute.model.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Customer-facing dispute list item. Does not include description, decision or timeline —
 * those belong to the detail response only.
 */
public record CustomerDisputeListItemDto(
        UUID id,
        CustomerTransactionSummary transaction,
        String reason,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt) {}
