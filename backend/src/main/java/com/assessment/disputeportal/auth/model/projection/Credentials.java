package com.assessment.disputeportal.auth.model.projection;

import java.util.UUID;

public record Credentials(
    UUID id, String email, String passwordHash, String role, boolean enabled, long authVersion) {
  @Override
  public String toString() {
    return "Credentials[redacted]";
  }
}
