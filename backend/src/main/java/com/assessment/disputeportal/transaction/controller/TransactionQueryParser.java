package com.assessment.disputeportal.transaction.controller;

import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.transaction.model.TransactionDirection;
import com.assessment.disputeportal.transaction.model.TransactionStatus;
import com.assessment.disputeportal.transaction.model.query.TransactionQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class TransactionQueryParser {
  private static final Set<String> ALLOWED =
      Set.of(
          "page",
          "size",
          "search",
          "direction",
          "status",
          "fromDate",
          "toDate",
          "minAmount",
          "maxAmount",
          "sortBy",
          "sortDirection",
          "accountId");
  private static final int MAX_PAGE_SIZE = 100;
  private static final long MAX_OFFSET = Integer.MAX_VALUE;
  private static final String UUID_PATTERN =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  public TransactionQuery parse(Map<String, List<String>> parameters) {
    if (!ALLOWED.containsAll(parameters.keySet())) throw invalid();
    parameters.forEach(
        (key, values) -> {
          if (values == null || values.size() != 1) throw invalid();
        });
    int page = integer(single(parameters, "page", "1"), 1, Integer.MAX_VALUE);
    int size = integer(single(parameters, "size", "20"), 1, MAX_PAGE_SIZE);
    long offset = ((long) page - 1) * size;
    if (offset > MAX_OFFSET) throw invalid();
    String search =
        normalizeSearch(
            parameters.containsKey("search") ? parameters.get("search").getFirst() : "");
    if (search.codePointCount(0, search.length()) > 100) throw invalid();
    TransactionDirection direction =
        enumValue(parameters, "direction", "ALL", TransactionDirection.class, true);
    TransactionStatus status =
        enumValue(parameters, "status", "ALL", TransactionStatus.class, true);
    LocalDate from = date(parameters, "fromDate");
    LocalDate to = date(parameters, "toDate");
    if (from != null
        && to != null
        && (to.isBefore(from) || to.toEpochDay() - from.toEpochDay() + 1 > 366)) throw invalid();
    BigDecimal min = amount(parameters, "minAmount");
    BigDecimal max = amount(parameters, "maxAmount");
    if (min != null && max != null && max.compareTo(min) < 0) throw invalid();
    TransactionQuery.SortField sort = sortField(single(parameters, "sortBy", "occurredAt"));
    TransactionQuery.SortDirection sortDirection;
    try {
      sortDirection =
          TransactionQuery.SortDirection.valueOf(single(parameters, "sortDirection", "DESC"));
    } catch (IllegalArgumentException failure) {
      throw invalid();
    }
    UUID accountId = uuid(parameters, "accountId");
    return new TransactionQuery(
        page,
        size,
        (int) offset,
        search,
        direction,
        status,
        from,
        to,
        min,
        max,
        sort,
        sortDirection,
        accountId);
  }

  public UUID parsePathId(String value) {
    if (value == null || !value.matches(UUID_PATTERN)) throw invalid();
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException failure) {
      throw invalid();
    }
  }

  private static String single(Map<String, List<String>> params, String name, String fallback) {
    List<String> values = params.get(name);
    if (values == null) return fallback;
    String value = values.getFirst();
    if (value == null || value.isEmpty()) throw invalid();
    return value;
  }

  private static int integer(String value, int minimum, int maximum) {
    if (!value.matches("[0-9]+")) throw invalid();
    try {
      int parsed = Integer.parseInt(value);
      if (parsed < minimum || parsed > maximum) throw invalid();
      return parsed;
    } catch (NumberFormatException failure) {
      throw invalid();
    }
  }

  private static String normalizeSearch(String value) {
    StringBuilder normalized = new StringBuilder();
    boolean pendingSpace = false;
    for (int i = 0; i < value.length(); ) {
      int cp = value.codePointAt(i);
      i += Character.charCount(cp);
      if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
        pendingSpace = normalized.length() > 0;
      } else {
        if (pendingSpace) normalized.append(' ');
        normalized.appendCodePoint(cp);
        pendingSpace = false;
      }
    }
    return normalized.toString();
  }

  private static <E extends Enum<E>> E enumValue(
      Map<String, List<String>> params,
      String key,
      String fallback,
      Class<E> type,
      boolean allAllowed) {
    String value = single(params, key, fallback);
    if (allAllowed && value.equals("ALL")) return null;
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException failure) {
      throw invalid();
    }
  }

  private static LocalDate date(Map<String, List<String>> params, String key) {
    List<String> values = params.get(key);
    if (values == null) return null;
    try {
      return LocalDate.parse(single(params, key, ""));
    } catch (DateTimeParseException failure) {
      throw invalid();
    }
  }

  private static BigDecimal amount(Map<String, List<String>> params, String key) {
    List<String> values = params.get(key);
    if (values == null) return null;
    String value = single(params, key, "");
    if (!value.matches("[0-9]{1,17}(?:\\.[0-9]{1,2})?")) throw invalid();
    try {
      return new BigDecimal(value).setScale(2);
    } catch (NumberFormatException failure) {
      throw invalid();
    }
  }

  private static TransactionQuery.SortField sortField(String value) {
    return switch (value) {
      case "occurredAt" -> TransactionQuery.SortField.OCCURRED_AT;
      case "amount" -> TransactionQuery.SortField.AMOUNT;
      case "merchant" -> TransactionQuery.SortField.MERCHANT;
      case "reference" -> TransactionQuery.SortField.REFERENCE;
      default -> throw invalid();
    };
  }

  private static UUID uuid(Map<String, List<String>> params, String key) {
    List<String> values = params.get(key);
    if (values == null) return null;
    String value = single(params, key, "");
    if (!value.matches(UUID_PATTERN)) throw invalid();
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException failure) {
      throw invalid();
    }
  }

  private static InvalidRequestException invalid() {
    return new InvalidRequestException("Invalid transaction query.");
  }
}
