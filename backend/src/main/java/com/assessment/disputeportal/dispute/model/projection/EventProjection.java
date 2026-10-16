package com.assessment.disputeportal.dispute.model.projection;

import java.time.Instant;

/**
 * Customer-safe read projection from the {@code dispute_events} table.
 *
 * <p>Reads only: dispute_version, event_type, customer_message, created_at.
 *
 * <p>Excluded: actor_id, from_status, to_status, previous_agent_id, new_agent_id, internal_notes.
 */
public record EventProjection(
    long disputeVersion, String eventType, String customerMessage, Instant createdAt) {}
