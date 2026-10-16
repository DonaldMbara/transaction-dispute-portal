package com.assessment.disputeportal.dispute.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assessment.disputeportal.common.exception.DomainInvariantException;
import com.assessment.disputeportal.common.exception.InvalidOperationException;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DisputeLifecycleTest {
  private static final Instant CREATED = Instant.parse("2026-10-11T08:35:00Z");
  private static final UUID AGENT = UUID.randomUUID();

  private Dispute open() {
    return new Dispute(
        UUID.randomUUID(),
        UUID.randomUUID(),
        DisputeReason.OTHER,
        "Customer description",
        DisputeStatus.OPEN,
        CREATED,
        CREATED,
        0);
  }

  @Test
  void creationRemainsOpenUnassignedAtVersionZero() {
    Dispute dispute = open();
    assertThat(dispute.status()).isEqualTo(DisputeStatus.OPEN);
    assertThat(dispute.assignedAgentId()).isNull();
    assertThat(dispute.version()).isZero();
  }

  @Test
  void claimReleaseAndResolveIncrementExactlyOnceAndRetainTerminalAssignment() {
    Dispute claimed = open().claim(AGENT, CREATED.plusSeconds(1));
    assertThat(claimed.status()).isEqualTo(DisputeStatus.UNDER_REVIEW);
    assertThat(claimed.version()).isEqualTo(1);
    assertThat(claimed.assignedAt()).isEqualTo(CREATED.plusSeconds(1));

    Dispute released =
        claimed.release(AGENT, "Reassign for specialist review", CREATED.plusSeconds(2));
    assertThat(released.status()).isEqualTo(DisputeStatus.OPEN);
    assertThat(released.assignedAgentId()).isNull();
    assertThat(released.assignedAt()).isNull();
    assertThat(released.version()).isEqualTo(2);

    Dispute resolved =
        released
            .claim(AGENT, CREATED.plusSeconds(3))
            .resolve(
                AGENT,
                DisputeStatus.ACCEPTED,
                "Evidence confirms duplicate debit.",
                "Your dispute was accepted after review.",
                CREATED.plusSeconds(4));
    assertThat(resolved.status()).isEqualTo(DisputeStatus.ACCEPTED);
    assertThat(resolved.version()).isEqualTo(4);
    assertThat(resolved.assignedAgentId()).isEqualTo(AGENT);
    assertThat(resolved.assignedAt()).isEqualTo(CREATED.plusSeconds(3));
    assertThat(resolved.resolvedAt()).isEqualTo(CREATED.plusSeconds(4));
  }

  @Test
  void rejectsInvalidStateAndWrongAgentTransitions() {
    Dispute open = open();
    assertThatThrownBy(() -> open.release(AGENT, "reason", CREATED.plusSeconds(1)))
        .isInstanceOf(InvalidOperationException.class);
    assertThatThrownBy(
            () ->
                open.resolve(
                    AGENT, DisputeStatus.REJECTED, "note", "message", CREATED.plusSeconds(1)))
        .isInstanceOf(InvalidOperationException.class);
    Dispute claimed = open.claim(AGENT, CREATED.plusSeconds(1));
    assertThatThrownBy(() -> claimed.release(UUID.randomUUID(), "reason", CREATED.plusSeconds(2)))
        .isInstanceOf(InvalidOperationException.class);
    assertThatThrownBy(
            () ->
                claimed.resolve(
                    AGENT, DisputeStatus.OPEN, "note", "message", CREATED.plusSeconds(2)))
        .isInstanceOf(InvalidOperationException.class);
  }

  @Test
  void trimsAndBoundsTransitionTextAndTerminalStateCannotChange() {
    Dispute claimed = open().claim(AGENT, CREATED.plusSeconds(1));
    Dispute resolved =
        claimed.resolve(
            AGENT,
            DisputeStatus.REJECTED,
            "  Internal review  ",
            "  Decision message  ",
            CREATED.plusSeconds(2));
    assertThat(resolved.internalResolutionNotes()).isEqualTo("Internal review");
    assertThat(resolved.customerResolutionMessage()).isEqualTo("Decision message");
    assertThatThrownBy(
            () -> resolved.resolve(AGENT, DisputeStatus.ACCEPTED, "n", "m", CREATED.plusSeconds(3)))
        .isInstanceOf(InvalidOperationException.class);
    assertThatThrownBy(() -> claimed.release(AGENT, " ", CREATED.plusSeconds(2)))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
            () -> claimed.resolve(AGENT, DisputeStatus.ACCEPTED, "n", " ", CREATED.plusSeconds(2)))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void auditEventsCarryTheMatchingVersionAndAssignmentTransition() {
    Dispute before = open();
    Dispute after = before.claim(AGENT, CREATED.plusSeconds(1));
    DisputeEvent event =
        DisputeEvent.transitioned(UUID.randomUUID(), before, after, AGENT, null, null);
    assertThat(event.eventType()).isEqualTo(DisputeEvent.EventType.CLAIMED);
    assertThat(event.disputeVersion()).isEqualTo(after.version());
    assertThat(event.fromStatus()).isEqualTo(DisputeStatus.OPEN);
    assertThat(event.toStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
    assertThat(event.newAgentId()).isEqualTo(AGENT);
    Dispute other =
        new Dispute(
                UUID.randomUUID(),
                before.transactionId(),
                before.reason(),
                before.description(),
                DisputeStatus.OPEN,
                CREATED,
                CREATED,
                0)
            .claim(AGENT, CREATED.plusSeconds(2));
    assertThatThrownBy(
            () -> DisputeEvent.transitioned(UUID.randomUUID(), before, other, AGENT, null, null))
        .isInstanceOf(DomainInvariantException.class);
  }
}
