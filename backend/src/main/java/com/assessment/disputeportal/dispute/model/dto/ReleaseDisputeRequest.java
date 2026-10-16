package com.assessment.disputeportal.dispute.model.dto;

import jakarta.validation.constraints.NotNull;

public record ReleaseDisputeRequest(@NotNull String reason) {}
