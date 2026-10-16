package com.assessment.disputeportal.dispute.exception;

public final class DisputeApiException extends RuntimeException {
  private final int status;
  private final int errorCode;
  private final String type;
  private final String title;
  private final String detail;
  private final boolean retryAfter;

  public DisputeApiException(int status, int errorCode, String type, String title, String detail) {
    this(status, errorCode, type, title, detail, false);
  }

  public DisputeApiException(
      int status, int errorCode, String type, String title, String detail, boolean retryAfter) {
    super(detail);
    this.status = status;
    this.errorCode = errorCode;
    this.type = type;
    this.title = title;
    this.detail = detail;
    this.retryAfter = retryAfter;
  }

  public int status() {
    return status;
  }

  public int errorCode() {
    return errorCode;
  }

  public String type() {
    return type;
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
        400,
        1004,
        "validation-failed",
        "Invalid request",
        "Check the dispute details and try again.");
  }

  public static DisputeApiException alreadyExists() {
    return new DisputeApiException(
        409,
        2003,
        "dispute-already-exists",
        "Dispute already exists",
        "This transaction already has a dispute. Reload its details to view the case.");
  }

  public static DisputeApiException keyReused() {
    return new DisputeApiException(
        409,
        2004,
        "idempotency-key-reused",
        "Submission key already used",
        "This key was already used for different details. Use a new key for the changed submission.");
  }

  public static DisputeApiException ineligible() {
    return new DisputeApiException(
        422,
        2002,
        "transaction-not-eligible",
        "Transaction not eligible",
        "Only completed transactions can be disputed.");
  }

  public static DisputeApiException unavailable() {
    return new DisputeApiException(
        503,
        3002,
        "service-unavailable",
        "Service temporarily unavailable",
        "The service is temporarily unavailable. Retry this unchanged submission.",
        true);
  }

  public static DisputeApiException preconditionRequired() {
    return new DisputeApiException(
        428,
        2007,
        "precondition-required",
        "Version required",
        "Reload this case before making a change.");
  }

  public static DisputeApiException staleVersion() {
    return new DisputeApiException(
        412,
        2006,
        "version-stale",
        "Case changed",
        "This case has changed. Reload it and review the latest details.");
  }

  public static DisputeApiException stateConflict() {
    return new DisputeApiException(
        409,
        2005,
        "dispute-state-conflict",
        "Case cannot be changed",
        "This case is no longer available for that action.");
  }
}
