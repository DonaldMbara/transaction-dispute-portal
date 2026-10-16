package com.assessment.disputeportal.security.session;

import com.assessment.disputeportal.auth.model.AccountRole;
import java.util.UUID;

/** Transient server request state; never serialized or sent to a client. */
public record ValidatedSession(
    String sessionId, UUID accountId, long version, AccountRole role, long accessTime) {
  public static final String ATTRIBUTE = ValidatedSession.class.getName();

  @Override
  public String toString() {
    return "ValidatedSession[accountId=" + accountId + "]";
  }
}
