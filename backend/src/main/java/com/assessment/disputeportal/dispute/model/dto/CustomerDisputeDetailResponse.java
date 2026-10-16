package com.assessment.disputeportal.dispute.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Customer-facing dispute detail response. Extends the list item fields with description, a
 * nullable decision (populated only for terminal disputes), and a curated timeline.
 *
 * <p>No agent/internal fields are present: no internalResolutionNotes, no assignedAgent, no
 * assignedAt, no internalNotes, no actorId, no previousAgentId, no newAgentId.
 */
public record CustomerDisputeDetailResponse(
    UUID id,
    CustomerTransactionResponse transaction,
    String reason,
    String status,
    Instant createdAt,
    Instant updatedAt,
    Instant resolvedAt,
    String description,
    CustomerDecision decision,
    List<CustomerTimelineItemResponse> timeline) {}
