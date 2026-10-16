package com.assessment.disputeportal.dispute.model.dto;

import com.assessment.disputeportal.dispute.model.DisputeReason;
import com.assessment.disputeportal.dispute.model.DisputeStatus;
import java.time.Instant;
import java.util.UUID;

public record DisputeSubmissionResponse(
    UUID id,
    UUID transactionId,
    DisputeReason reason,
    String description,
    DisputeStatus status,
    Instant createdAt) {}
