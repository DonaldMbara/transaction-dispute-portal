package com.assessment.disputeportal.auth.repository;

import java.util.Optional;
import java.util.UUID;

public interface AccountCredentialsRepository {
    Optional<Credentials> findByEmail(String canonicalEmail);

    record Credentials(UUID id, String email, String passwordHash, String role, boolean enabled, long authVersion) {
        @Override
        public String toString() {
            return "Credentials[redacted]";
        }
    }
}