package com.assessment.disputeportal.dispute.service;

import com.fasterxml.jackson.databind.JsonNode;

public record DisputeSubmissionResult(JsonNode envelope) {
    public String disputeId() { return envelope.path("data").path("id").asText(); }
}
