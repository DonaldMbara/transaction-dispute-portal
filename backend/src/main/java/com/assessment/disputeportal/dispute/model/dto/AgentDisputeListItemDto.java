package com.assessment.disputeportal.dispute.model.dto;

import java.time.Instant;
import java.util.UUID;

public record AgentDisputeListItemDto(
    UUID id,
    String reference,
    String merchant,
    String amount,
    String currency,
    String direction,
    String reason,
    String status,
    Instant createdAt,
    Instant updatedAt,
    Instant resolvedAt,
    AgentIdentitySummary assignedAgent,
    long version) {}
