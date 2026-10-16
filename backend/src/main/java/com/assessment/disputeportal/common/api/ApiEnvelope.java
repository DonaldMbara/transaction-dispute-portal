package com.assessment.disputeportal.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiEnvelope(
    boolean status,
    Object data,
    int error_code,
    @JsonInclude(JsonInclude.Include.NON_NULL) ApiError error) {

  public static ApiEnvelope success(Object data) {
    return new ApiEnvelope(true, data, 0, null);
  }

  public static ApiEnvelope failure(int code, String type, String title, String detail) {
    return new ApiEnvelope(false, null, code, new ApiError("/problems/" + type, title, detail));
  }

  public static ApiEnvelope unavailable() {
    return failure(
        3002,
        "service-unavailable",
        "Service temporarily unavailable",
        "The service is temporarily unavailable. Please try again.");
  }
}
