package com.assessment.disputeportal.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.UUID;

public interface DisputeIdempotencyRepository {
  Optional<IdempotencyResult> find(UUID userId, String operation, String key);

  void save(UUID userId, String operation, String key, String hash, JsonNode responseBody);
}
