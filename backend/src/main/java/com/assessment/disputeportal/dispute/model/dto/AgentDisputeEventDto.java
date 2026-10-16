package com.assessment.disputeportal.dispute.model.dto;

import java.time.Instant;
import java.util.UUID;

public record AgentDisputeEventDto(
    UUID id,
    long disputeVersion,
    String eventType,
    UUID actorId,
    String fromStatus,
    String toStatus,
    UUID previousAgentId,
    UUID newAgentId,
    String internalNotes,
    String customerMessage,
    Instant createdAt) {}
