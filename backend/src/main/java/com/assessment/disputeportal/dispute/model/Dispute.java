package com.assessment.disputeportal.dispute.model;

import com.assessment.disputeportal.common.exception.DomainInvariantException;
import com.assessment.disputeportal.common.exception.InvalidOperationException;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/** Immutable dispute aggregate. Each successful lifecycle transition returns a new version. */
@Builder(toBuilder = true)
public record Dispute(
    UUID id,
    UUID transactionId,
    DisputeReason reason,
    String description,
    DisputeStatus status,
    Instant createdAt,
    Instant updatedAt,
    long version,
    UUID assignedAgentId,
    Instant assignedAt,
    Instant resolvedAt,
    String internalResolutionNotes,
    String customerResolutionMessage) {
  private static final int MAX_RELEASE_REASON = 2000;
  private static final int MAX_INTERNAL_NOTES = 4000;
  private static final int MAX_CUSTOMER_MESSAGE = 2000;

  public Dispute {
    Objects.requireNonNull(id);
    Objects.requireNonNull(transactionId);
    Objects.requireNonNull(reason);
    Objects.requireNonNull(description);
    Objects.requireNonNull(status);
    Objects.requireNonNull(createdAt);
    Objects.requireNonNull(updatedAt);
    if (description.isBlank()
        || description.length() > 2000
        || version < 0
        || updatedAt.isBefore(createdAt)) {
      throw new DomainInvariantException("Invalid dispute state.");
    }
    boolean validLifecycle =
        switch (status) {
          case OPEN ->
              assignedAgentId == null
                  && assignedAt == null
                  && resolvedAt == null
                  && internalResolutionNotes == null
                  && customerResolutionMessage == null;
          case UNDER_REVIEW ->
              assignedAgentId != null
                  && assignedAt != null
                  && resolvedAt == null
                  && internalResolutionNotes == null
                  && customerResolutionMessage == null;
          case ACCEPTED, REJECTED ->
              assignedAgentId != null
                  && assignedAt != null
                  && resolvedAt != null
                  && internalResolutionNotes != null
                  && customerResolutionMessage != null;
        };
    if (!validLifecycle)
      throw new DomainInvariantException("Inconsistent dispute lifecycle fields.");
  }

  /** Retains the original creation call shape used by dispute submission. */
  public Dispute(
      UUID id,
      UUID transactionId,
      DisputeReason reason,
      String description,
      DisputeStatus status,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this(
        id,
        transactionId,
        reason,
        description,
        status,
        createdAt,
        updatedAt,
        version,
        null,
        null,
        null,
        null,
        null);
  }

  public Dispute claim(UUID agentId, Instant now) {
    Objects.requireNonNull(agentId);
    Objects.requireNonNull(now);
    require(
        status == DisputeStatus.OPEN && assignedAgentId == null,
        "Only an open unassigned dispute can be claimed.");
    require(!now.isBefore(updatedAt), "Transition time cannot precede the previous update.");
    return toBuilder()
        .status(DisputeStatus.UNDER_REVIEW)
        .updatedAt(now)
        .version(nextVersion())
        .assignedAgentId(agentId)
        .assignedAt(now)
        .resolvedAt(null)
        .internalResolutionNotes(null)
        .customerResolutionMessage(null)
        .build();
  }

  public Dispute release(UUID agentId, String reason, Instant now) {
    requireAssignedUnderReview(agentId);
    normalizeReleaseReason(reason);
    require(!now.isBefore(updatedAt), "Transition time cannot precede the previous update.");
    return toBuilder()
        .status(DisputeStatus.OPEN)
        .updatedAt(now)
        .version(nextVersion())
        .assignedAgentId(null)
        .assignedAt(null)
        .resolvedAt(null)
        .internalResolutionNotes(null)
        .customerResolutionMessage(null)
        .build();
  }

  public static String normalizeReleaseReason(String reason) {
    return requiredText(reason, MAX_RELEASE_REASON, "Release reason");
  }

  public Dispute resolve(
      UUID agentId,
      DisputeStatus decision,
      String internalNotes,
      String customerMessage,
      Instant now) {
    requireAssignedUnderReview(agentId);
    require(
        decision == DisputeStatus.ACCEPTED || decision == DisputeStatus.REJECTED,
        "A dispute decision must be ACCEPTED or REJECTED.");
    String normalizedNotes =
        requiredText(internalNotes, MAX_INTERNAL_NOTES, "Internal resolution notes");
    String normalizedMessage =
        requiredText(customerMessage, MAX_CUSTOMER_MESSAGE, "Customer resolution message");
    Objects.requireNonNull(now);
    require(!now.isBefore(updatedAt), "Transition time cannot precede the previous update.");
    return toBuilder()
        .status(decision)
        .updatedAt(now)
        .version(nextVersion())
        .resolvedAt(now)
        .internalResolutionNotes(normalizedNotes)
        .customerResolutionMessage(normalizedMessage)
        .build();
  }

  private void requireAssignedUnderReview(UUID agentId) {
    Objects.requireNonNull(agentId);
    require(
        status == DisputeStatus.UNDER_REVIEW && assignedAgentId != null,
        "Only an under-review dispute can be changed.");
    require(assignedAgentId.equals(agentId), "Only the assigned agent can change this dispute.");
  }

  private long nextVersion() {
    if (version == Long.MAX_VALUE) throw new DomainInvariantException("Dispute version exhausted.");
    return version + 1;
  }

  private static String requiredText(String value, int max, String field) {
    if (value == null) throw new InvalidRequestException(field + " is required.");
    String normalized = value.strip();
    if (normalized.isEmpty() || normalized.length() > max) {
      throw new InvalidRequestException(
          field + " must contain between 1 and " + max + " characters.");
    }
    return normalized;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new InvalidOperationException(message);
  }
}
