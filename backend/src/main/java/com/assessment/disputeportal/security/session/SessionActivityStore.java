package com.assessment.disputeportal.security.session;

import com.assessment.disputeportal.auth.model.AccountIdentity;
import com.assessment.disputeportal.auth.model.AccountRole;
import java.util.Optional;
import java.util.UUID;

/**
 * Session activity metadata. No entity, hash or mutable Spring Session object crosses this
 * boundary.
 */
public interface SessionActivityStore {
  long IDLE_MILLIS = 1_800_000L;

  Optional<SessionActivitySnapshot> inspect(String publicSessionId);

  /** Returns false when the credential-verified account changed before session activation. */
  boolean activate(String publicSessionId, AccountIdentity account, long accessTime);

  boolean renew(
      String publicSessionId, UUID accountId, long version, AccountRole role, long accessTime);
}
