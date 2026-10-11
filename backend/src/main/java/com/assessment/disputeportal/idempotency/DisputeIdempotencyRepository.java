package com.assessment.disputeportal.idempotency;

import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public interface DisputeIdempotencyRepository {
    Optional<IdempotencyResult> find(UUID userId, String operation, String key);
    void save(UUID userId, String operation, String key, String hash, JsonNode responseBody);

    record IdempotencyResult(String hash, JsonNode responseBody) {}
}
