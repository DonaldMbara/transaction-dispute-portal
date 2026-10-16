package com.assessment.disputeportal.auth.cli;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.assessment.disputeportal.auth.model.Account;
import com.assessment.disputeportal.auth.model.AccountIdentity;
import com.assessment.disputeportal.auth.model.AccountRole;
import com.assessment.disputeportal.auth.validation.PasswordPolicy;
import com.assessment.disputeportal.auth.validation.EmailNormalizer;
import com.assessment.disputeportal.auth.repository.AccountRepository;
import com.assessment.disputeportal.common.exception.BusinessConflictException;
import com.assessment.disputeportal.common.exception.InvalidOperationException;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.common.exception.ResourceNotFoundException;

/** Host-operated service, deliberately not an HTTP controller or startup runner. */
public final class AccountAdministrationService {

    private static final Logger AUDIT = LoggerFactory.getLogger("account.administration.audit");

    private final AccountRepository accounts;
    private final EmailNormalizer emails;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transaction;

    public AccountAdministrationService(AccountRepository accounts, EmailNormalizer emails,
            PasswordEncoder passwords, PlatformTransactionManager transactions) {
        this.accounts    = accounts;
        this.emails      = emails;
        this.passwords   = passwords;
        this.transaction = new TransactionTemplate(transactions);
    }

    public AccountIdentity create(String email, String role, boolean enabled, CharSequence password, String operator) {
        UUID id = UUID.randomUUID();
        return audited("create", id, operator, () -> {
            String canonical  = emails.normalize(email);
            AccountRole accountRole = AccountRole.parse(role);
            String hash       = passwords.encode(PasswordPolicy.validateForCreation(password));
            Account domain    = new Account(id, canonical, hash, accountRole, enabled, Instant.now(), 0L);
            return transaction.execute(status -> {
                accounts.insert(domain);
                return domain.identity();
            });
        });
    }

    public AccountIdentity setEnabled(UUID id, long expectedVersion, boolean enabled, String operator) {
        return audited(enabled ? "enable" : "disable", id, operator,
                () -> mutate(id, expectedVersion, account -> account.replaceEnabled(enabled)));
    }

    public AccountIdentity setRole(UUID id, long expectedVersion, String role, String operator) {
        return audited("set-role", id, operator, () -> {
            AccountRole accountRole = AccountRole.parse(role);
            return mutate(id, expectedVersion, account -> account.replaceRole(accountRole));
        });
    }

    public AccountIdentity replacePassword(UUID id, long expectedVersion, CharSequence password, String operator) {
        return audited("replace-password", id, operator, () -> {
            Objects.requireNonNull(id);
            if (expectedVersion < 0) throw new InvalidRequestException("Invalid account version.");
            // Expensive hashing runs outside the transaction; version is rechecked after hashing inside mutate().
            String hash = passwords.encode(PasswordPolicy.validateForCreation(password));
            return mutate(id, expectedVersion, account -> account.replacePassword(hash));
        });
    }

    private AccountIdentity mutate(UUID id, long expectedVersion, Consumer<Account> mutation) {
        Objects.requireNonNull(id);
        if (expectedVersion < 0) throw new InvalidRequestException("Invalid account version.");
        return transaction.execute(status -> {
                Account domain = accounts.findByIdForUpdate(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
            if (domain.authVersion() != expectedVersion) {
                throw new BusinessConflictException("Account changed; inspect it before retrying.");
            }
            mutation.accept(domain);
            accounts.save(domain);
            return domain.identity();
        });
    }

    private AccountIdentity audited(String action, UUID account, String operator, Supplier<AccountIdentity> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new InvalidOperationException("Account administration requires its own transaction boundary.");
        }
        UUID correlation = UUID.randomUUID();
        try {
            AccountIdentity result = work.get();
            recordOutcome(action, account, operator, correlation, "committed");
            return result;
        } catch (RuntimeException failure) {
            recordOutcome(action, account, operator, correlation, "failed");
            throw failure;
        }
    }

    public static void recordOutcome(String action, UUID account, String operator, UUID correlation, String outcome) {
        String actor = operator != null && operator.matches("[A-Za-z0-9_.@-]{1,64}") ? operator : "host-operator";
        AUDIT.info("timestamp={} operator={} action={} account={} outcome={} correlation={}",
                Instant.now(), actor, action, account, outcome, correlation);
    }
}
