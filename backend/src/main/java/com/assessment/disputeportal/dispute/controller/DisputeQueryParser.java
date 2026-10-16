package com.assessment.disputeportal.dispute.controller;

import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.dispute.model.query.DisputeQuery;
import com.assessment.disputeportal.dispute.model.query.StatusPredicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Validates and parses raw HTTP query parameters into an immutable {@link DisputeQuery}.
 *
 * <p>Throws {@link InvalidRequestException} (400/1004) for any of:
 *
 * <ul>
 *   <li>Unknown query parameters
 *   <li>Duplicate parameter values
 *   <li>Non-UUID accountId
 *   <li>Unknown status value
 *   <li>Reversed date ranges
 *   <li>Invalid page/size or offset overflow
 * </ul>
 */
@Component
class DisputeQueryParser {

  private static final ZoneId JOHANNESBURG = ZoneId.of("Africa/Johannesburg");
  private static final int MAX_SEARCH_LENGTH = 100;
  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 100;
  private static final String DEFAULT_SORT_FIELD = "createdAt";
  private static final String DEFAULT_SORT_DIRECTION = "DESC";

  private static final Set<String> ALLOWED_PARAMS =
      Set.of(
          "page",
          "size",
          "accountId",
          "status",
          "reason",
          "fromDate",
          "toDate",
          "search",
          "sortBy",
          "sortDirection");

  private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "updatedAt", "status");
  private static final Map<String, String> SORT_FIELD_ALIASES =
      Map.of(
          "createdAt", "d.created_at",
          "updatedAt", "d.updated_at",
          "status", "d.status");

  private static final Pattern DSP_PREFIX = Pattern.compile("^dsp-", Pattern.CASE_INSENSITIVE);

  /**
   * Parses query parameters from the given Web request for the dispute list endpoint. The {@code
   * customerId} must be derived from the session principal before calling this method.
   */
  public DisputeQuery parse(Map<String, String[]> parameterMap, UUID customerId) {
    validateNoUnknownParams(parameterMap);
    validateNoDuplicates(parameterMap);

    UUID accountId = parseAccountId(parameterMap);
    StatusPredicate statusPredicate = parseStatus(parameterMap);
    String reason = parseSingleParam(parameterMap, "reason", "ALL");
    LocalDate fromDate = parseLocalDate(parameterMap, "fromDate");
    LocalDate toDate = parseLocalDate(parameterMap, "toDate");
    validateDateRange(fromDate, toDate);

    String rawSearch = parseSingleParam(parameterMap, "search", null);
    SearchPatterns patterns = buildSearchPatterns(rawSearch);

    String sortBy = parseSortBy(parameterMap);
    String sortDirection = parseSortDirection(parameterMap);
    PageRequest pageRequest = buildPageRequest(parameterMap, sortBy, sortDirection);

    Instant fromUtc = fromDate != null ? fromDate.atStartOfDay(JOHANNESBURG).toInstant() : null;
    Instant toExclusiveUtc =
        toDate != null ? toDate.plusDays(1).atStartOfDay(JOHANNESBURG).toInstant() : null;

    return new DisputeQuery(
        customerId,
        accountId,
        statusPredicate,
        reason,
        fromUtc,
        toExclusiveUtc,
        patterns.refSearchPattern(),
        patterns.subPattern(),
        sortBy,
        sortDirection,
        pageRequest);
  }

  // ---------------------------------------------------------------------------
  // Validation helpers
  // ---------------------------------------------------------------------------

  private void validateNoUnknownParams(Map<String, String[]> params) {
    for (String key : params.keySet()) {
      if (!ALLOWED_PARAMS.contains(key)) {
        throw new InvalidRequestException("Unknown query parameter: " + key);
      }
    }
  }

  private void validateNoDuplicates(Map<String, String[]> params) {
    for (Map.Entry<String, String[]> entry : params.entrySet()) {
      if (entry.getValue() != null && entry.getValue().length > 1) {
        throw new InvalidRequestException("Duplicate query parameter: " + entry.getKey());
      }
    }
  }

  private UUID parseAccountId(Map<String, String[]> params) {
    String raw = firstValue(params, "accountId");
    if (raw == null) return null;
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException("Invalid accountId: must be a UUID.");
    }
  }

  private StatusPredicate parseStatus(Map<String, String[]> params) {
    String raw = firstValue(params, "status");
    if (raw == null || "ALL".equalsIgnoreCase(raw)) {
      return StatusPredicate.NONE;
    }
    return switch (raw.toUpperCase()) {
      case "ACTIVE" -> StatusPredicate.ACTIVE;
      case "RESOLVED" -> StatusPredicate.RESOLVED;
      case "OPEN" -> new StatusPredicate.Individual("OPEN");
      case "UNDER_REVIEW" -> new StatusPredicate.Individual("UNDER_REVIEW");
      case "ACCEPTED" -> new StatusPredicate.Individual("ACCEPTED");
      case "REJECTED" -> new StatusPredicate.Individual("REJECTED");
      default -> throw new InvalidRequestException("Unknown status value: " + raw);
    };
  }

  private LocalDate parseLocalDate(Map<String, String[]> params, String name) {
    String raw = firstValue(params, name);
    if (raw == null || raw.isBlank()) return null;
    try {
      return LocalDate.parse(raw);
    } catch (Exception e) {
      throw new InvalidRequestException("Invalid date value for " + name + ": " + raw);
    }
  }

  private void validateDateRange(LocalDate from, LocalDate to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new InvalidRequestException("fromDate must not be after toDate.");
    }
  }

  private String parseSortBy(Map<String, String[]> params) {
    String raw = firstValue(params, "sortBy");
    if (raw == null) return DEFAULT_SORT_FIELD;
    if (!ALLOWED_SORT_FIELDS.contains(raw)) {
      throw new InvalidRequestException("Unknown sortBy value: " + raw);
    }
    return raw;
  }

  private String parseSortDirection(Map<String, String[]> params) {
    String raw = firstValue(params, "sortDirection");
    if (raw == null) return DEFAULT_SORT_DIRECTION;
    if (!"ASC".equalsIgnoreCase(raw) && !"DESC".equalsIgnoreCase(raw)) {
      throw new InvalidRequestException("sortDirection must be ASC or DESC.");
    }
    return raw.toUpperCase();
  }

  private PageRequest buildPageRequest(
      Map<String, String[]> params, String sortBy, String sortDirection) {
    int page = parsePositiveInt(params, "page", DEFAULT_PAGE);
    int size = parseSize(params);
    if (page < 1) throw new InvalidRequestException("page must be >= 1.");
    // Guard against offset overflow (signed 32-bit limit: ~2.1 billion rows)
    long offset = (long) (page - 1) * size;
    if (offset > Integer.MAX_VALUE) {
      throw new InvalidRequestException("Page offset exceeds the supported range.");
    }
    Sort.Direction direction =
        "ASC".equals(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
    return PageRequest.of(page - 1, size, Sort.by(direction, sortBy));
  }

  private int parseSize(Map<String, String[]> params) {
    int size = parsePositiveInt(params, "size", DEFAULT_SIZE);
    if (size < 1 || size > MAX_SIZE) {
      throw new InvalidRequestException("size must be between 1 and " + MAX_SIZE + ".");
    }
    return size;
  }

  private int parsePositiveInt(Map<String, String[]> params, String name, int defaultValue) {
    String raw = firstValue(params, name);
    if (raw == null) return defaultValue;
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException e) {
      throw new InvalidRequestException("Invalid value for " + name + ": must be an integer.");
    }
  }

  private String parseSingleParam(Map<String, String[]> params, String name, String defaultValue) {
    String value = firstValue(params, name);
    return value != null ? value : defaultValue;
  }

  // ---------------------------------------------------------------------------
  // Search normalization
  // ---------------------------------------------------------------------------

  private record SearchPatterns(String refSearchPattern, String subPattern) {}

  private SearchPatterns buildSearchPatterns(String rawSearch) {
    if (rawSearch == null || rawSearch.isBlank()) {
      return new SearchPatterns(null, null);
    }

    // Trim and collapse internal whitespace
    String normalized = rawSearch.strip().replaceAll("\\s+", " ");
    if (normalized.length() > MAX_SEARCH_LENGTH) {
      throw new InvalidRequestException(
          "Search term must be at most " + MAX_SEARCH_LENGTH + " characters.");
    }
    if (normalized.isEmpty()) {
      return new SearchPatterns(null, null);
    }

    // Strip leading DSP- prefix (case-insensitive) for reference search
    String withoutPrefix = DSP_PREFIX.matcher(normalized).replaceFirst("");

    // Escape ILIKE special characters in the reference portion
    String escapedRef = escapeLike(withoutPrefix);
    String escapedSub = escapeLike(normalized);

    // Reference: starts-with match against d.id::text
    String refPattern = escapedRef + "%";
    // Substring: match against description / merchant / transaction reference
    String subPattern = "%" + escapedSub + "%";

    return new SearchPatterns(refPattern, subPattern);
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  // ---------------------------------------------------------------------------
  // Utility
  // ---------------------------------------------------------------------------

  private static String firstValue(Map<String, String[]> params, String key) {
    String[] values = params.get(key);
    return (values != null && values.length > 0) ? values[0] : null;
  }
}
