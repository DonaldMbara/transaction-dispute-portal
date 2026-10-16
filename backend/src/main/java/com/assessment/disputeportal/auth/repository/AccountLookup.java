package com.assessment.disputeportal.auth.repository;

import com.assessment.disputeportal.auth.model.AccountIdentity;
import java.util.Optional;
import java.util.UUID;

/** Security-facing lookups deliberately exclude credential hashes and JPA entities. */
public interface AccountLookup {
  Optional<AccountIdentity> findById(UUID id);

  Optional<AccountIdentity> findByEmail(String email);
}
