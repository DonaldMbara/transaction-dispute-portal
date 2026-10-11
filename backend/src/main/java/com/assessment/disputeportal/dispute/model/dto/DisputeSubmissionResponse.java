package com.assessment.disputeportal.dispute.model.dto;

import java.time.Instant;
import java.util.UUID;

import com.assessment.disputeportal.dispute.model.DisputeReason;
import com.assessment.disputeportal.dispute.model.DisputeStatus;

public record DisputeSubmissionResponse(UUID id, UUID transactionId, DisputeReason reason,
        String description, DisputeStatus status, Instant createdAt) {}
