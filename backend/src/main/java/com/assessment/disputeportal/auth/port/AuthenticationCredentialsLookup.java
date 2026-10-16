package com.assessment.disputeportal.auth.port;

import com.assessment.disputeportal.auth.model.projection.Credentials;
import java.util.Optional;

public interface AuthenticationCredentialsLookup {
  Optional<Credentials> findByEmail(String canonicalEmail);
}
