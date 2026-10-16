package com.assessment.disputeportal.dispute.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.assessment.disputeportal.dispute.model.dto.CustomerTimelineEntry;
import com.assessment.disputeportal.dispute.model.projection.EventProjection;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerTimelineMapperTest {

  private final CustomerTimelineMapper mapper = new CustomerTimelineMapper();

  private static final Instant T1 = Instant.parse("2026-10-06T07:15:00Z");
  private static final Instant T2 = Instant.parse("2026-10-07T08:00:00Z");
  private static final Instant T3 = Instant.parse("2026-10-07T12:30:00Z");
  private static final Instant T4 = Instant.parse("2026-10-08T07:00:00Z");
  private static final Instant T5 = Instant.parse("2026-10-09T14:00:00Z");

  @Test
  void emptyInputReturnsEmptyList() {
    assertThat(mapper.map(List.of())).isEmpty();
  }

  @Test
  void nullInputReturnsEmptyList() {
    assertThat(mapper.map(null)).isEmpty();
  }

  @Test
  void singleCreatedEventMapsToSubmitted() {
    var events = List.of(new EventProjection(0, "CREATED", null, T1));

    List<CustomerTimelineEntry> result = mapper.map(events);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).type()).isEqualTo("SUBMITTED");
    assertThat(result.get(0).message()).isEqualTo("We received your dispute.");
    assertThat(result.get(0).occurredAt()).isEqualTo(T1);
  }

  @Test
  void singleClaimedEventMapsToInReviewWithBeingReviewedMessage() {
    var events = List.of(new EventProjection(1, "CLAIMED", null, T2));

    List<CustomerTimelineEntry> result = mapper.map(events);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).type()).isEqualTo("IN_REVIEW");
    assertThat(result.get(0).message()).isEqualTo("Your transaction was being reviewed.");
    assertThat(result.get(0).occurredAt()).isEqualTo(T2);
  }

  @Test
  void twoClaimedEventsWithInterveningReleasedProducesCorrectMessages() {
    var events =
        List.of(
            new EventProjection(0, "CREATED", null, T1),
            new EventProjection(1, "CLAIMED", null, T2),
            new EventProjection(2, "RELEASED", null, T3),
            new EventProjection(3, "CLAIMED", null, T4));

    List<CustomerTimelineEntry> result = mapper.map(events);

    assertThat(result).hasSize(4);

    assertThat(result.get(0).type()).isEqualTo("SUBMITTED");

    assertThat(result.get(1).type()).isEqualTo("IN_REVIEW");
    assertThat(result.get(1).message()).isEqualTo("Your transaction was being reviewed.");
    assertThat(result.get(1).occurredAt()).isEqualTo(T2);

    assertThat(result.get(2).type()).isEqualTo("RETURNED_TO_QUEUE");
    assertThat(result.get(2).message()).isEqualTo("Your dispute was waiting for review again.");
    assertThat(result.get(2).occurredAt()).isEqualTo(T3);

    assertThat(result.get(3).type()).isEqualTo("IN_REVIEW");
    assertThat(result.get(3).message()).isEqualTo("Review of your dispute restarted.");
    assertThat(result.get(3).occurredAt()).isEqualTo(T4);
  }

  @Test
  void resolvedAcceptedMapsToResolvedWithCustomerMessage() {
    String customerMsg = "A duplicate charge was confirmed. This case is now resolved.";
    var events = List.of(new EventProjection(1, "RESOLVED_ACCEPTED", customerMsg, T5));

    List<CustomerTimelineEntry> result = mapper.map(events);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).type()).isEqualTo("RESOLVED");
    assertThat(result.get(0).message()).isEqualTo(customerMsg);
    assertThat(result.get(0).occurredAt()).isEqualTo(T5);
  }

  @Test
  void resolvedRejectedMapsToResolvedWithCustomerMessage() {
    String customerMsg = "We could not confirm a duplicate charge.";
    var events = List.of(new EventProjection(1, "RESOLVED_REJECTED", customerMsg, T5));

    List<CustomerTimelineEntry> result = mapper.map(events);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).type()).isEqualTo("RESOLVED");
    assertThat(result.get(0).message()).isEqualTo(customerMsg);
    assertThat(result.get(0).occurredAt()).isEqualTo(T5);
  }

  @Test
  void unknownEventTypeIsSkipped() {
    var events =
        List.of(
            new EventProjection(0, "CREATED", null, T1),
            new EventProjection(1, "UNKNOWN_FUTURE_EVENT", null, T2),
            new EventProjection(2, "RESOLVED_ACCEPTED", "Resolved.", T3));

    List<CustomerTimelineEntry> result = mapper.map(events);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).type()).isEqualTo("SUBMITTED");
    assertThat(result.get(1).type()).isEqualTo("RESOLVED");
  }

  @Test
  void multipleUnknownEventsAreAllSkipped() {
    var events =
        List.of(
            new EventProjection(0, "FUTURE_TYPE_A", null, T1),
            new EventProjection(1, "FUTURE_TYPE_B", null, T2));

    assertThat(mapper.map(events)).isEmpty();
  }

  @Test
  void releasedMapsToReturnedToQueueWithGenericMessage() {
    var events = List.of(new EventProjection(2, "RELEASED", null, T3));

    List<CustomerTimelineEntry> result = mapper.map(events);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).type()).isEqualTo("RETURNED_TO_QUEUE");
    assertThat(result.get(0).message()).isEqualTo("Your dispute was waiting for review again.");
  }

  @Test
  void fullLifecycleMapsCorrectly() {
    String resolvedMsg = "Your dispute has been accepted.";
    var events =
        List.of(
            new EventProjection(0, "CREATED", null, T1),
            new EventProjection(1, "CLAIMED", null, T2),
            new EventProjection(2, "RELEASED", null, T3),
            new EventProjection(3, "CLAIMED", null, T4),
            new EventProjection(4, "RESOLVED_ACCEPTED", resolvedMsg, T5));

    List<CustomerTimelineEntry> result = mapper.map(events);

    assertThat(result).hasSize(5);
    assertThat(result.get(0).type()).isEqualTo("SUBMITTED");
    assertThat(result.get(1).type()).isEqualTo("IN_REVIEW");
    assertThat(result.get(1).message()).isEqualTo("Your transaction was being reviewed.");
    assertThat(result.get(2).type()).isEqualTo("RETURNED_TO_QUEUE");
    assertThat(result.get(3).type()).isEqualTo("IN_REVIEW");
    assertThat(result.get(3).message()).isEqualTo("Review of your dispute restarted.");
    assertThat(result.get(4).type()).isEqualTo("RESOLVED");
    assertThat(result.get(4).message()).isEqualTo(resolvedMsg);
  }
}
