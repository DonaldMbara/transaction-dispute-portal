package com.assessment.disputeportal.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Bounded, per-process exponential backoff for login attempts. */
@Component
public final class InMemoryLoginAttemptLimiter implements LoginAttemptLimiter {
  private static final int IP_FAILURE_THRESHOLD = 20;
  private static final int IDENTITY_FAILURE_THRESHOLD = 5;
  private static final Duration BASE_DELAY = Duration.ofSeconds(1);
  private static final Duration MAX_DELAY = Duration.ofMinutes(15);
  private static final Duration ENTRY_TTL = Duration.ofHours(1);

  private final Cache<String, AttemptState> attempts =
      Caffeine.newBuilder().maximumSize(100_000).expireAfterAccess(ENTRY_TTL).build();
  private final Clock clock;

  public InMemoryLoginAttemptLimiter() {
    this(Clock.systemUTC());
  }

  InMemoryLoginAttemptLimiter(Clock clock) {
    this.clock = clock;
  }

  @Override
  public Optional<Duration> retryAfter(String clientAddress, String normalizedEmail) {
    Instant now = clock.instant();
    return keys(clientAddress, normalizedEmail).stream()
        .map(attempts::getIfPresent)
        .filter(state -> state != null && state.blockedUntil().isAfter(now))
        .map(state -> Duration.between(now, state.blockedUntil()))
        .map(InMemoryLoginAttemptLimiter::wholeSeconds)
        .max(Duration::compareTo);
  }

  @Override
  public Optional<Duration> recordFailure(String clientAddress, String normalizedEmail) {
    Instant now = clock.instant();
    for (String key : keys(clientAddress, normalizedEmail)) {
      attempts.asMap().compute(key, (unused, current) -> nextState(current, now, key));
    }
    return retryAfter(clientAddress, normalizedEmail);
  }

  @Override
  public void recordSuccess(String clientAddress, String normalizedEmail) {
    attempts.invalidate(combinedKey(clientAddress, normalizedEmail));
  }

  private static AttemptState nextState(AttemptState current, Instant now, String key) {
    int threshold = key.startsWith("ip:") ? IP_FAILURE_THRESHOLD : IDENTITY_FAILURE_THRESHOLD;
    int failures =
        current == null || current.lastFailureAt().plus(ENTRY_TTL).isBefore(now)
            ? 1
            : current.failures() + 1;
    if (failures < threshold) return new AttemptState(failures, now, Instant.MIN);
    long multiplier = 1L << Math.min(failures - threshold, 20);
    Duration delay = BASE_DELAY.multipliedBy(multiplier);
    if (delay.compareTo(MAX_DELAY) > 0) delay = MAX_DELAY;
    return new AttemptState(failures, now, now.plus(delay));
  }

  private static List<String> keys(String clientAddress, String normalizedEmail) {
    return List.of(
        fingerprint("ip", clientAddress),
        fingerprint("email", normalizedEmail),
        combinedKey(clientAddress, normalizedEmail));
  }

  private static String combinedKey(String clientAddress, String normalizedEmail) {
    return fingerprint("pair", clientAddress + "\u0000" + normalizedEmail);
  }

  private static String fingerprint(String type, String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest((type + "\u0000" + value).getBytes(StandardCharsets.UTF_8));
      return type + ":" + HexFormat.of().formatHex(digest, 0, 12);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable.", impossible);
    }
  }

  private static Duration wholeSeconds(Duration duration) {
    return Duration.ofSeconds(Math.max(1, (duration.toMillis() + 999) / 1000));
  }

  private record AttemptState(int failures, Instant lastFailureAt, Instant blockedUntil) {}
}
