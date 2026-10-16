package com.assessment.disputeportal.security.session;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Credential-free version-one serialized identity; current authorities come from the account store.
 */
public record SessionPrincipal(UUID accountId) implements Serializable {
  @Serial private static final long serialVersionUID = 1L;

  public SessionPrincipal {
    Objects.requireNonNull(accountId, "Account identifier required.");
  }
}
