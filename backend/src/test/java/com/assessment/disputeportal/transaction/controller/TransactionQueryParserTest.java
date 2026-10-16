package com.assessment.disputeportal.transaction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.transaction.model.TransactionDirection;
import com.assessment.disputeportal.transaction.model.query.TransactionQuery;
import com.assessment.disputeportal.transaction.model.TransactionStatus;
import org.junit.jupiter.api.Test;

class TransactionQueryParserTest {
    private final TransactionQueryParser parser = new TransactionQueryParser();

    @Test
    void parsesDefaultsAndReturnsAnImmutableQueryValue() {
        var query = parser.parse(Map.of());
        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(20);
        assertThat(query.offset()).isZero();
        assertThat(query.direction()).isNull();
        assertThat(query.status()).isNull();
        assertThat(query.sortBy()).isEqualTo(TransactionQuery.SortField.OCCURRED_AT);
        assertThat(query.sortDirection()).isEqualTo(TransactionQuery.SortDirection.DESC);
    }

    @Test
    void normalizesUnicodeWhitespaceAndAllowsEmptySearch() {
        assertThat(parse("search", " \u2003Shop\u00a0  One \t").search()).isEqualTo("Shop One");
        assertThat(parse("search", " \t\u2003").search()).isEmpty();
        assertThatThrownBy(() -> parse("search", "x".repeat(101))).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void parsesSupportedFiltersAndBounds() {
        var query = parser.parse(Map.ofEntries(
                Map.entry("page", List.of("3")), Map.entry("size", List.of("100")),
                Map.entry("direction", List.of("IN")), Map.entry("status", List.of("COMPLETED")),
                Map.entry("fromDate", List.of("2025-01-01")), Map.entry("toDate", List.of("2026-01-01")),
                Map.entry("minAmount", List.of("0")), Map.entry("maxAmount", List.of("99999999999999999.99")),
                Map.entry("sortBy", List.of("amount")), Map.entry("sortDirection", List.of("ASC")),
                Map.entry("accountId", List.of("10000000-0000-0000-0000-000000000001"))));
        assertThat(query.offset()).isEqualTo(200);
        assertThat(query.direction()).isEqualTo(TransactionDirection.IN);
        assertThat(query.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(query.minAmount().toPlainString()).isEqualTo("0.00");
        assertThat(query.maxAmount().toPlainString()).isEqualTo("99999999999999999.99");
        assertThat(query.accountId()).isEqualTo(UUID.fromString("10000000-0000-0000-0000-000000000001"));
        assertThat(parse("fromDate", "2025-03-01").fromDate()).isNotNull();
    }

    @Test
    void rejectsInvalidOrDuplicateParametersAndOverflow() {
        for (Map<String, List<String>> params : List.of(
                Map.of("extra", List.of("x")), Map.of("page", List.of("1", "2")),
                Map.of("accountId", List.of("10000000-0000-0000-0000-000000000001", "10000000-0000-0000-0000-000000000002")),
                Map.of("accountId", List.of("not-a-uuid")), Map.of("page", List.of("0")),
                Map.of("size", List.of("101")), Map.of("page", List.of("2147483647"), "size", List.of("100")),
                Map.of("direction", List.of("sideways")), Map.of("status", List.of("ALL ")),
                Map.of("fromDate", List.of("2025-02-30")),
                Map.of("fromDate", List.of("2025-01-01"), "toDate", List.of("2025-01-02"), "minAmount", List.of("2"), "maxAmount", List.of("1")),
                Map.of("fromDate", List.of("2025-01-01"), "toDate", List.of("2026-01-02")),
                Map.of("minAmount", List.of("-1")), Map.of("minAmount", List.of("1.001")),
                Map.of("maxAmount", List.of("100000000000000000.00")), Map.of("sortBy", List.of("id; drop table")),
                Map.of("sortDirection", List.of("desc")))) {
            assertThatThrownBy(() -> parser.parse(params)).isInstanceOf(InvalidRequestException.class);
        }
    }

    @Test
    void validatesCanonicalPathIdsAndRejectsUnsupportedDetailQueriesAtControllerBoundary() {
        assertThat(parser.parsePathId("10000000-0000-0000-0000-000000000001")).isEqualTo(
                UUID.fromString("10000000-0000-0000-0000-000000000001"));
        assertThatThrownBy(() -> parser.parsePathId("1-1-1-1-1")).isInstanceOf(InvalidRequestException.class);
    }

    private TransactionQuery parse(String key, String value) {
        return parser.parse(Map.of(key, List.of(value)));
    }
}
