package com.assessment.disputeportal.auth.service;

import java.time.Duration;
import java.util.Optional;

/** Tracks login failures by client, normalized email, and their combination. */
public interface LoginAttemptLimiter {
  Optional<Duration> retryAfter(String clientAddress, String normalizedEmail);

  Optional<Duration> recordFailure(String clientAddress, String normalizedEmail);

  void recordSuccess(String clientAddress, String normalizedEmail);
}
