package com.assessment.disputeportal.auth.model.dto;

import jakarta.validation.constraints.NotNull;

/** Strict login body. */
public record LoginRequest(@NotNull String email, @NotNull String password) {
  @Override
  public String toString() {
    return "LoginRequest[redacted]";
  }
}
