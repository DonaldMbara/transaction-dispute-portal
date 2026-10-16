package com.assessment.disputeportal.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryLoginAttemptLimiterTest {
  private final MutableClock clock = new MutableClock();
  private final InMemoryLoginAttemptLimiter limiter = new InMemoryLoginAttemptLimiter(clock);

  @Test
  void backsOffByEmailAndPairAndSuccessfulLoginClearsOnlyThePair() {
    for (int i = 0; i < 4; i++) {
      assertThat(limiter.recordFailure("192.0.2.1", "user@example.test")).isEmpty();
    }
    assertThat(limiter.recordFailure("192.0.2.1", "user@example.test"))
        .contains(Duration.ofSeconds(1));

    limiter.recordSuccess("192.0.2.1", "user@example.test");

    assertThat(limiter.retryAfter("192.0.2.1", "user@example.test"))
        .contains(Duration.ofSeconds(1));
    assertThat(limiter.retryAfter("192.0.2.1", "other@example.test")).isEmpty();
    assertThat(limiter.retryAfter("192.0.2.2", "user@example.test"))
        .contains(Duration.ofSeconds(1));
  }

  @Test
  void throttleExpiresAndTheNextFailureUsesAnIncreasingDelay() {
    for (int i = 0; i < 5; i++) {
      limiter.recordFailure("192.0.2.1", "user@example.test");
    }
    clock.advance(Duration.ofSeconds(2));
    assertThat(limiter.retryAfter("192.0.2.1", "user@example.test")).isEmpty();
    assertThat(limiter.recordFailure("192.0.2.1", "user@example.test"))
        .contains(Duration.ofSeconds(2));
  }

  private static final class MutableClock extends Clock {
    private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }
  }
}
