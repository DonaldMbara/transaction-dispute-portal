package com.assessment.disputeportal.transaction.model;

import java.util.List;
import com.assessment.disputeportal.transaction.repository.TransactionReadRow;

public record TransactionPage(long total, int page, int size, List<TransactionReadRow> items) {
    public TransactionPage { items = List.copyOf(items); }
}
