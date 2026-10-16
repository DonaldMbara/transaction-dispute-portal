package com.assessment.disputeportal.common.exception;

import java.time.Duration;

public final class RateLimitedException extends RuntimeException {
  private final Duration retryAfter;

  public RateLimitedException(Duration retryAfter) {
    super("Login attempts are temporarily limited.");
    this.retryAfter = retryAfter;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
