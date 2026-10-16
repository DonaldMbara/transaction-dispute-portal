package com.assessment.disputeportal.common.exception;

import com.assessment.disputeportal.common.api.ErrorCode;

public final class DisputeApiException extends RuntimeException implements CodedException {
  private final ErrorCode errorCode;
  private final String title;
  private final String detail;
  private final boolean retryAfter;

  private DisputeApiException(ErrorCode errorCode, String title, String detail) {
    this(errorCode, title, detail, false);
  }

  private DisputeApiException(
      ErrorCode errorCode, String title, String detail, boolean retryAfter) {
    super(detail);
    this.errorCode = errorCode;
    this.title = title;
    this.detail = detail;
    this.retryAfter = retryAfter;
  }

  @Override
  public ErrorCode errorCode() {
    return errorCode;
  }

  public String title() {
    return title;
  }

  public String detail() {
    return detail;
  }

  public boolean retryAfter() {
    return retryAfter;
  }

  public static DisputeApiException invalid() {
    return new DisputeApiException(
        ErrorCode.INVALID_REQUEST, "Invalid request", "Check the dispute details and try again.");
  }

  public static DisputeApiException alreadyExists() {
    return new DisputeApiException(
        ErrorCode.DISPUTE_ALREADY_EXISTS,
        "Dispute already exists",
        "This transaction already has a dispute. Reload its details to view the case.");
  }

  public static DisputeApiException keyReused() {
    return new DisputeApiException(
        ErrorCode.IDEMPOTENCY_KEY_REUSED,
        "Submission key already used",
        "This key was already used for different details. Use a new key for the changed submission.");
  }

  public static DisputeApiException ineligible() {
    return new DisputeApiException(
        ErrorCode.TRANSACTION_NOT_ELIGIBLE,
        "Transaction not eligible",
        "Only completed transactions can be disputed.");
  }

  public static DisputeApiException unavailable() {
    return new DisputeApiException(
        ErrorCode.STORE_UNAVAILABLE,
        "Service temporarily unavailable",
        "The service is temporarily unavailable. Retry this unchanged submission.",
        true);
  }

  public static DisputeApiException preconditionRequired() {
    return new DisputeApiException(
        ErrorCode.PRECONDITION_REQUIRED,
        "Version required",
        "Reload this case before making a change.");
  }

  public static DisputeApiException staleVersion() {
    return new DisputeApiException(
        ErrorCode.VERSION_STALE,
        "Case changed",
        "This case has changed. Reload it and review the latest details.");
  }

  public static DisputeApiException stateConflict() {
    return new DisputeApiException(
        ErrorCode.DISPUTE_STATE_CONFLICT,
        "Case cannot be changed",
        "This case is no longer available for that action.");
  }
}
