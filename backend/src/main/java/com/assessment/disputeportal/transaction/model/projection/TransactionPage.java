package com.assessment.disputeportal.transaction.model.projection;

import java.util.List;

public record TransactionPage(long total, int page, int size, List<TransactionReadRow> items) {
  public TransactionPage {
    items = List.copyOf(items);
  }
}
