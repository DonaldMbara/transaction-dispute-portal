package com.assessment.disputeportal.dispute.model.query;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

/**
 * Immutable parsed query object produced by {@code DisputeQueryParser}. All authorization and
 * validation has been applied before construction.
 *
 * <p>{@code accountId} is null when the caller did not supply one (all owned accounts).
 *
 * <p>{@code fromUtc}/{@code toExclusiveUtc} are null when no date range was requested.
 *
 * <p>{@code refSearchPattern}/{@code subPattern} are null when no search term was supplied.
 */
public record DisputeQuery(
    UUID authenticatedCustomerId,
    UUID accountId,
    StatusPredicate statusPredicate,
    String reason,
    Instant fromUtc,
    Instant toExclusiveUtc,
    String refSearchPattern,
    String subPattern,
    String sortField,
    String sortDirection,
    PageRequest pageRequest) {}
