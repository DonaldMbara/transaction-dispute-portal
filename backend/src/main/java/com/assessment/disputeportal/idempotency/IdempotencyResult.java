package com.assessment.disputeportal.idempotency;

import com.fasterxml.jackson.databind.JsonNode;

public record IdempotencyResult(String hash, JsonNode responseBody) {}