package com.assessment.disputeportal.dispute.model.query;

import java.util.List;

/**
 * Encodes the status filter strategy parsed from the raw {@code status} query parameter.
 *
 * <ul>
 *   <li>{@link None} — no status predicate (ALL or absent)
 *   <li>{@link Individual} — single exact match
 *   <li>{@link InList} — IN clause for group aliases ACTIVE or RESOLVED
 * </ul>
 */
public sealed interface StatusPredicate {

  /** No status filter — all disputes returned. */
  record None() implements StatusPredicate {}

  /** Exact equality: {@code d.status = :status} */
  record Individual(String status) implements StatusPredicate {}

  /** IN clause: {@code d.status IN (...)} — used for ACTIVE and RESOLVED group aliases. */
  record InList(List<String> statuses) implements StatusPredicate {}

  // Convenience constants for the common group aliases
  StatusPredicate NONE = new None();
  StatusPredicate ACTIVE = new InList(List.of("OPEN", "UNDER_REVIEW"));
  StatusPredicate RESOLVED = new InList(List.of("ACCEPTED", "REJECTED"));
}
