package com.assessment.disputeportal.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.assessment.disputeportal.auth.validation.EmailNormalizer;
import com.assessment.disputeportal.auth.model.AccountIdentity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class JpaAccountLookup implements AccountLookup {

    private final AppUserRepository repository;
    private final EmailNormalizer emails;

    @Override
    public Optional<AccountIdentity> findById(UUID id) {
        return repository.identityById(Objects.requireNonNull(id));
    }

    @Override
    public Optional<AccountIdentity> findByEmail(String email) {
        return repository.identityByEmail(emails.normalize(email));
    }
}
