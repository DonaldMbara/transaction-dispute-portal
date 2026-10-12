package com.assessment.disputeportal.dispute.mapper;

// Schema baseline: V7 (V7__create_disputes_and_idempotency.sql)
// Reads: dispute_version, event_type, customer_message, created_at
// Excluded: actor_id, from_status, to_status, previous_agent_id, new_agent_id, internal_notes

import com.assessment.disputeportal.dispute.model.dto.CustomerTimelineEntry;
import com.assessment.disputeportal.dispute.model.dto.EventProjection;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps a list of customer-safe {@link EventProjection} rows (ordered by
 * {@code dispute_version ASC, created_at ASC}) to a curated list of
 * {@link CustomerTimelineEntry} objects for the customer-facing timeline.
 *
 * <p>Internal notes, agent identities and release reasons are never read or copied.</p>
 */
@Component
public class CustomerTimelineMapper {

    /**
     * Maps ordered event projections to customer-safe timeline entries.
     *
     * @param events events ordered by {@code dispute_version ASC, created_at ASC}
     * @return curated timeline entries in the same order; unknown event types are skipped
     */
    public List<CustomerTimelineEntry> map(List<EventProjection> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        List<CustomerTimelineEntry> timeline = new ArrayList<>(events.size());
        int claimCount = 0;

        for (EventProjection event : events) {
            switch (event.eventType()) {
                case "CREATED" -> timeline.add(new CustomerTimelineEntry(
                        "SUBMITTED",
                        "We received your dispute.",
                        event.createdAt()));

                case "CLAIMED" -> {
                    String message = claimCount == 0
                            ? "Your transaction was being reviewed."
                            : "Review of your dispute restarted.";
                    timeline.add(new CustomerTimelineEntry("IN_REVIEW", message, event.createdAt()));
                    claimCount++;
                }

                case "RELEASED" -> timeline.add(new CustomerTimelineEntry(
                        "RETURNED_TO_QUEUE",
                        "Your dispute was waiting for review again.",
                        event.createdAt()));

                case "RESOLVED_ACCEPTED", "RESOLVED_REJECTED" -> timeline.add(new CustomerTimelineEntry(
                        "RESOLVED",
                        event.customerMessage(),
                        event.createdAt()));

                default -> { /* skip unknown event types for forward compatibility */ }
            }
        }

        return List.copyOf(timeline);
    }
}
