package com.assessment.disputeportal.dispute.model.dto;

import java.time.Instant;

/** A single curated milestone in the customer-visible dispute timeline. */
public record CustomerTimelineEntry(String type, String message, Instant occurredAt) {}
