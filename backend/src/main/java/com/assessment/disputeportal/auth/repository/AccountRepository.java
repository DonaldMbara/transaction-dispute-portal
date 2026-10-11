package com.assessment.disputeportal.auth.repository;

import java.util.Optional;
import java.util.UUID;

import com.assessment.disputeportal.auth.model.Account;

public interface AccountRepository {
    void insert(Account account);

    /** Requires an active transaction that remains open through the matching save. */
    Optional<Account> findByIdForUpdate(UUID id);

    void save(Account account);
}