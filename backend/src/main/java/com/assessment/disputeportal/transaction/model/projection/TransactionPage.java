package com.assessment.disputeportal.transaction.model.projection;

import java.util.List;
import com.assessment.disputeportal.transaction.model.projection.TransactionReadRow;

public record TransactionPage(long total, int page, int size, List<TransactionReadRow> items) {
    public TransactionPage { items = List.copyOf(items); }
}
