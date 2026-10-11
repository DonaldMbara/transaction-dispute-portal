package com.assessment.disputeportal.dispute.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DisputeCreatedEvent(UUID id, UUID disputeId, UUID actorId, Instant occurredAt) {
    public DisputeCreatedEvent {
        Objects.requireNonNull(id);
        Objects.requireNonNull(disputeId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(occurredAt);
    }
}
