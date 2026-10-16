package com.assessment.disputeportal.dispute.model.dto;

import com.assessment.disputeportal.dispute.model.DisputeReason;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SubmitDisputeRequest(
    @NotNull UUID transactionId, @NotNull DisputeReason reason, @NotNull String description) {}
