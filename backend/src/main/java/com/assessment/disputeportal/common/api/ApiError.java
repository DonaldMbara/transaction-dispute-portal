package com.assessment.disputeportal.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Safe error details using the relative /problems/{slug} type namespace.")
public record ApiError(
    @Schema(description = "Relative problem type URI.") String type,
    @Schema(description = "Short human-readable summary.") String title,
    @Schema(description = "Safe actionable detail; never includes secrets or private notes.")
        String detail) {}
