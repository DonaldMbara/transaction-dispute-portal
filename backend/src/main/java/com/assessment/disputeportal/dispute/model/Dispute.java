package com.assessment.disputeportal.dispute.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Dispute(UUID id, UUID transactionId, DisputeReason reason, String description,
        DisputeStatus status, Instant createdAt, Instant updatedAt, long version) {
    public Dispute {
        Objects.requireNonNull(id);
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(description);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (description.isBlank() || description.length() > 2000 || status != DisputeStatus.OPEN || version != 0) {
            throw new IllegalArgumentException("Invalid initial dispute state.");
        }
    }
}
