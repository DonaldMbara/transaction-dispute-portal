package com.assessment.disputeportal.common.api;

import java.util.List;

public record PageResponse<T>(long total, int page, int size, List<T> items) {
  public PageResponse {
    items = List.copyOf(items);
  }
}
