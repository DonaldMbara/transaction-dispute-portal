package com.assessment.disputeportal.dispute.model.dto;

import jakarta.validation.constraints.NotNull;

public record ResolveDisputeRequest(
    @NotNull String decision,
    @NotNull String internalResolutionNotes,
    @NotNull String customerResolutionMessage) {}
