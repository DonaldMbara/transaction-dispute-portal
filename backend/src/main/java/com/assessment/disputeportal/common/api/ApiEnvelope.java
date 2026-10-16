package com.assessment.disputeportal.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Standard success or error envelope for API responses.")
public record ApiEnvelope<T>(
    @Schema(description = "True when the operation succeeded.") boolean status,
    @Schema(description = "Response payload; null on failure.") T data,
    @Schema(
            description =
                "Zero on success; otherwise a stable numeric code from docs/design/api-contract.md.",
            example = "0")
        int error_code,
    @Schema(description = "Safe problem details; omitted for successful responses.")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        ApiError error) {

  public static <T> ApiEnvelope<T> success(T data) {
    return new ApiEnvelope<>(true, data, 0, null);
  }

  public static ApiEnvelope<Void> failure(int code, String type, String title, String detail) {
    return new ApiEnvelope<>(false, null, code, new ApiError("/problems/" + type, title, detail));
  }

  public static ApiEnvelope<Void> unavailable() {
    var code = ErrorCode.STORE_UNAVAILABLE;
    return failure(
        code.code(),
        code.type(),
        "Service temporarily unavailable",
        "The service is temporarily unavailable. Please try again.");
  }
}
