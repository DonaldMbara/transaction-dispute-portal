package com.assessment.disputeportal.auth.repository;

import java.util.Optional;
import java.util.UUID;

import com.assessment.disputeportal.auth.model.projection.Credentials;

public interface AccountCredentialsRepository {
    Optional<Credentials> findByEmail(String canonicalEmail);
}