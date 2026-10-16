package com.assessment.disputeportal.common.api;

import org.springframework.http.HttpStatus;

/** The numeric error contract shared by domain exceptions and the HTTP layer. */
public enum ErrorCode {
  AUTHENTICATION_REQUIRED(1001, HttpStatus.UNAUTHORIZED, "unauthenticated"),
  FORBIDDEN(1002, HttpStatus.FORBIDDEN, "forbidden"),
  RESOURCE_NOT_FOUND(1003, HttpStatus.NOT_FOUND, "resource-not-found"),
  INVALID_REQUEST(1004, HttpStatus.BAD_REQUEST, "validation-failed"),
  RATE_LIMITED(1005, HttpStatus.TOO_MANY_REQUESTS, "rate-limited"),
  REQUEST_TOO_LARGE(1006, HttpStatus.PAYLOAD_TOO_LARGE, "request-too-large"),
  UNSUPPORTED_MEDIA_TYPE(1007, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type"),
  METHOD_NOT_ALLOWED(1008, HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed"),
  NOT_ACCEPTABLE(1009, HttpStatus.NOT_ACCEPTABLE, "not-acceptable"),
  CSRF_REJECTED(1010, HttpStatus.FORBIDDEN, "csrf-rejected"),
  TRANSACTION_NOT_ELIGIBLE(2002, HttpStatus.UNPROCESSABLE_ENTITY, "transaction-not-eligible"),
  DISPUTE_ALREADY_EXISTS(2003, HttpStatus.CONFLICT, "dispute-already-exists"),
  IDEMPOTENCY_KEY_REUSED(2004, HttpStatus.CONFLICT, "idempotency-key-reused"),
  DISPUTE_STATE_CONFLICT(2005, HttpStatus.CONFLICT, "dispute-state-conflict"),
  VERSION_STALE(2006, HttpStatus.PRECONDITION_FAILED, "version-stale"),
  PRECONDITION_REQUIRED(2007, HttpStatus.PRECONDITION_REQUIRED, "precondition-required"),
  ACCOUNT_STATE_CONFLICT(2008, HttpStatus.CONFLICT, "account-state-conflict"),
  INTERNAL_ERROR(3001, HttpStatus.INTERNAL_SERVER_ERROR, "internal-error"),
  STORE_UNAVAILABLE(3002, HttpStatus.SERVICE_UNAVAILABLE, "service-unavailable");

  private final int code;
  private final HttpStatus status;
  private final String type;

  ErrorCode(int code, HttpStatus status, String type) {
    this.code = code;
    this.status = status;
    this.type = type;
  }

  public int code() {
    return code;
  }

  public HttpStatus status() {
    return status;
  }

  public String type() {
    return type;
  }
}
