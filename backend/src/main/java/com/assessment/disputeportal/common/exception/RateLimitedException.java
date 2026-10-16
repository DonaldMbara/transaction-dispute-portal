package com.assessment.disputeportal.common.exception;

import com.assessment.disputeportal.common.api.ErrorCode;
import java.time.Duration;

public final class RateLimitedException extends RuntimeException implements CodedException {
  private final Duration retryAfter;

  public RateLimitedException(Duration retryAfter) {
    super("Login attempts are temporarily limited.");
    this.retryAfter = retryAfter;
  }

  public Duration retryAfter() {
    return retryAfter;
  }

  @Override
  public ErrorCode errorCode() {
    return ErrorCode.RATE_LIMITED;
  }
}
