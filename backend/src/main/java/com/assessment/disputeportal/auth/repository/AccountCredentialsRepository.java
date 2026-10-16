package com.assessment.disputeportal.auth.repository;

import com.assessment.disputeportal.auth.model.projection.Credentials;
import java.util.Optional;

public interface AccountCredentialsRepository {
  Optional<Credentials> findByEmail(String canonicalEmail);
}
