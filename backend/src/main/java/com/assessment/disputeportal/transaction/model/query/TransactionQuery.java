package com.assessment.disputeportal.transaction.model.query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import com.assessment.disputeportal.transaction.model.TransactionDirection;
import com.assessment.disputeportal.transaction.model.TransactionStatus;

public record TransactionQuery(int page, int size, int offset, String search,
        TransactionDirection direction, TransactionStatus status, LocalDate fromDate,
        LocalDate toDate, BigDecimal minAmount, BigDecimal maxAmount,
        SortField sortBy, SortDirection sortDirection, UUID accountId) {
    public enum SortField {
        OCCURRED_AT("occurred_at"), AMOUNT("amount"), MERCHANT("merchant"), REFERENCE("reference");
        private final String column;
        SortField(String column) { this.column = column; }
        public String column() { return column; }
    }
    public enum SortDirection {
        ASC, DESC
    }
}
