package com.assessment.disputeportal.auth.repository;

import java.util.Optional;

import com.assessment.disputeportal.auth.model.projection.Credentials;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class JpaAccountCredentialsRepository implements AccountCredentialsRepository {

    private final AppUserRepository repository;

    @Override
    public Optional<Credentials> findByEmail(String canonicalEmail) {
        return repository.findByEmail(canonicalEmail).map(account -> new Credentials(
                account.getId(), account.getEmail(), account.getPasswordHash(), account.getRole().name(),
                account.isEnabled(), account.getAuthVersion()));
    }
}