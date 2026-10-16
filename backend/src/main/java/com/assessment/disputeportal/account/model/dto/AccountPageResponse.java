package com.assessment.disputeportal.account.model.dto;

import java.util.List;

public record AccountPageResponse(long total, int page, int size, List<AccountResponse> items) {
  public AccountPageResponse {
    items = List.copyOf(items);
  }
}
