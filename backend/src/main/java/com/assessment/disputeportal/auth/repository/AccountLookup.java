package com.assessment.disputeportal.auth.repository;

import java.util.Optional;
import java.util.UUID;

import com.assessment.disputeportal.auth.model.AccountIdentity;

/** Security-facing lookups deliberately exclude credential hashes and JPA entities. */
public interface AccountLookup {
    Optional<AccountIdentity> findById(UUID id);
    Optional<AccountIdentity> findByEmail(String email);
}
