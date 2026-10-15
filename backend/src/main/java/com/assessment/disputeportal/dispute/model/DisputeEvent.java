package com.assessment.disputeportal.dispute.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable audit fact corresponding to one committed aggregate version. */
public record DisputeEvent(UUID id, UUID disputeId, UUID actorId, long disputeVersion,
        EventType eventType, DisputeStatus fromStatus, DisputeStatus toStatus,
        UUID previousAgentId, UUID newAgentId, String internalNotes,
        String customerMessage, Instant createdAt) {
    public enum EventType { CREATED, CLAIMED, RELEASED, RESOLVED_ACCEPTED, RESOLVED_REJECTED }

    public DisputeEvent {
        Objects.requireNonNull(id);
        Objects.requireNonNull(disputeId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(toStatus);
        Objects.requireNonNull(createdAt);
        if (disputeVersion < 0) throw new IllegalArgumentException("Event version cannot be negative.");
        if (eventType == EventType.CREATED && (disputeVersion != 0 || fromStatus != null
                || toStatus != DisputeStatus.OPEN || previousAgentId != null || newAgentId != null)) {
            throw new IllegalArgumentException("Invalid dispute creation event.");
        }
        if (eventType != EventType.CREATED && (fromStatus == null || disputeVersion == 0)) {
            throw new IllegalArgumentException("Transition events require a prior state and positive version.");
        }
    }

    public static DisputeEvent created(UUID id, UUID disputeId, UUID customerId, Instant now) {
        return new DisputeEvent(id, disputeId, customerId, 0, EventType.CREATED,
                null, DisputeStatus.OPEN, null, null, null, null, now);
    }

    public static DisputeEvent transitioned(UUID id, Dispute before, Dispute after, UUID actorId,
            String internalNotes, String customerMessage) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(after);
        if (!before.id().equals(after.id()) || after.version() != before.version() + 1) {
            throw new IllegalArgumentException("Event must describe exactly one dispute version.");
        }
        EventType type = switch (after.status()) {
            case UNDER_REVIEW -> EventType.CLAIMED;
            case OPEN -> EventType.RELEASED;
            case ACCEPTED -> EventType.RESOLVED_ACCEPTED;
            case REJECTED -> EventType.RESOLVED_REJECTED;
        };
        return new DisputeEvent(id, after.id(), actorId, after.version(), type,
                before.status(), after.status(), before.assignedAgentId(), after.assignedAgentId(),
                internalNotes, customerMessage, after.updatedAt());
    }
}
