package com.assessment.disputeportal.dispute.model.query;

import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable parsed query object produced by {@code DisputeQueryParser}.
 * All authorization and validation has been applied before construction.
 *
 * <p>{@code accountId} is null when the caller did not supply one (all owned accounts).</p>
 * <p>{@code fromUtc}/{@code toExclusiveUtc} are null when no date range was requested.</p>
 * <p>{@code refSearchPattern}/{@code subPattern} are null when no search term was supplied.</p>
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
