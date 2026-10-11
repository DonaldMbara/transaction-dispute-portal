package com.assessment.disputeportal.transaction.model.dto;

import java.util.List;

public record TransactionPageResponse(long total, int page, int size, List<TransactionResponse> items) {
    public TransactionPageResponse { items = List.copyOf(items); }
}
