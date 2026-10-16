package com.assessment.disputeportal.dispute.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentDisputeDetailResponse(
    UUID id,
    String reference,
    UUID transactionId,
    String merchant,
    String amount,
    String currency,
    String direction,
    Instant occurredAt,
    String transactionReference,
    UUID accountId,
    String accountDisplayName,
    String maskedAccountNumber,
    String reason,
    String status,
    String description,
    Instant createdAt,
    Instant updatedAt,
    Instant assignedAt,
    Instant resolvedAt,
    AgentIdentityResponse assignedAgent,
    String internalResolutionNotes,
    String customerResolutionMessage,
    long version,
    List<AgentDisputeEventResponse> events) {
  public AgentDisputeDetailResponse {
    events = List.copyOf(events);
  }
}
