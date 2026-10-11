package com.assessment.disputeportal.auth.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.assessment.disputeportal.auth.validation.PasswordPolicy;
import com.assessment.disputeportal.common.exception.BusinessConflictException;
import com.assessment.disputeportal.common.exception.DomainInvariantException;

/**
 * Domain aggregate for a user account.
 * Owns all business invariants: no-op guards, version advancement, hash format validation.
 * Has no JPA, Spring, or framework dependencies.
 */
public final class Account {

    private final UUID id;
    private final Instant createdAt;
    private String email;
    private String passwordHash;
    private AccountRole role;
    private boolean enabled;
    private long authVersion;

    public Account(UUID id, String email, String passwordHash, AccountRole role,
            boolean enabled, Instant createdAt, long authVersion) {
        this.id          = Objects.requireNonNull(id, "id required");
        this.email       = Objects.requireNonNull(email, "email required");
        this.createdAt   = Objects.requireNonNull(createdAt, "createdAt required");
        this.role        = Objects.requireNonNull(role, "role required");
        this.enabled     = enabled;
        this.authVersion = authVersion;
        setPasswordHash(passwordHash);
    }

    // --- queries ---

    public UUID id()           { return id; }
    public String email()      { return email; }
    public String passwordHash() { return passwordHash; }
    public AccountRole role()  { return role; }
    public boolean enabled()   { return enabled; }
    public Instant createdAt() { return createdAt; }
    public long authVersion()  { return authVersion; }

    public AccountIdentity identity() {
        return new AccountIdentity(id, email, role, enabled, authVersion);
    }

    // --- mutations ---

    public void replaceEnabled(boolean value) {
        if (enabled == value) throw new BusinessConflictException("Account already has the requested status.");
        advanceVersion();
        enabled = value;
    }

    public void replaceRole(AccountRole value) {
        if (value == null) throw new DomainInvariantException("Account role is required.");
        if (role == value) throw new BusinessConflictException("Account already has the requested role.");
        advanceVersion();
        role = value;
    }

    public void replacePassword(String encodedPassword) {
        setPasswordHash(encodedPassword);
        advanceVersion();
    }

    // --- internals ---

    private void setPasswordHash(String encodedPassword) {
        if (!PasswordPolicy.isSupportedHash(encodedPassword)) {
            throw new DomainInvariantException("Account password hash has an unsupported format.");
        }
        this.passwordHash = encodedPassword;
    }

    private void advanceVersion() {
        try {
            authVersion = Math.addExact(authVersion, 1);
        } catch (ArithmeticException overflow) {
            throw new DomainInvariantException("Account version cannot be advanced.", overflow);
        }
    }

    @Override
    public String toString() {
        return "Account[id=" + id + "]";
    }
}
