package com.assessment.disputeportal.account.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.assessment.disputeportal.account.AccountNumberMask;
import com.assessment.disputeportal.account.model.AccountType;
import com.assessment.disputeportal.account.port.AccountFixturePort;
import com.assessment.disputeportal.account.repository.AccountFixtureRepository;
import com.assessment.disputeportal.account.repository.AccountFixtureRepository.FixtureRow;
import com.assessment.disputeportal.auth.model.AccountIdentity;
import com.assessment.disputeportal.auth.model.AccountRole;
import com.assessment.disputeportal.auth.repository.AccountLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountFixtureService implements AccountFixturePort {
    private static final String NUMBER_NAMESPACE = "customer-accounts-number-v1";
    private static final long NUMBER_SPACE = BigInteger.TEN.pow(AccountNumberMask.ACCOUNT_NUMBER_LENGTH).longValueExact();
    private static final Instant MAIN_CREATED_AT = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant SAVINGS_CREATED_AT = Instant.parse("2025-01-02T00:00:00Z");

    private final AccountLookup customers;
    private final AccountFixtureRepository accounts;

    @Transactional
    public Map<String, UUID> createFixtureAccounts(String customerEmail) {
        AccountIdentity customer = requireEnabledCustomer(customerEmail);
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (AccountType type : AccountType.values()) {
            FixtureRow expected = expected(customer.id(), type, "");
            FixtureRow persisted = ensureFixtureRow(expected);
            ids.put(type.name(), persisted.id());
        }
        log.info("account.fixture action=ensure customerId={} outcome=committed", customer.id());
        return Map.copyOf(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, UUID> getFixtureAccountIds(String customerEmail) {
        AccountIdentity customer = requireEnabledCustomer(customerEmail);
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (AccountType type : AccountType.values()) {
            UUID id = accountId(customer.id(), type);
            FixtureRow row = accounts.findById(id)
                    .orElseThrow(() -> new AccountFixtureException("The customer fixture accounts are not available."));
            if (!row.customerId().equals(customer.id()) || !row.accountType().equals(type.name())) {
                throw new AccountFixtureException("The customer fixture accounts do not match their expected state.");
            }
            ids.put(type.name(), row.id());
        }
        return Map.copyOf(ids);
    }

    private AccountIdentity requireEnabledCustomer(String customerEmail) {
        AccountIdentity identity = customers.findByEmail(customerEmail)
                .filter(account -> account.role() == AccountRole.CUSTOMER && account.enabled())
                .orElseThrow(() -> new AccountFixtureException("An enabled CUSTOMER account is required."));
        return identity;
    }

    private FixtureRow ensureFixtureRow(FixtureRow expected) {
        long maximumAttempts = Math.min(NUMBER_SPACE, accounts.countAll() + 1);
        for (long probe = 0; probe < maximumAttempts; probe++) {
            String number = syntheticNumber(expected.customerId(), AccountType.valueOf(expected.accountType()), probe);
            FixtureRow candidate = new FixtureRow(expected.id(), expected.customerId(), number, expected.accountType(),
                    expected.balance(), expected.primary(), expected.createdAt());
            // Let PostgreSQL's global UNIQUE constraint arbitrate number collisions.
            // A conflict from another fixture row leaves this deterministic ID absent,
            // so advancing the probe is safe without querying by the full number.
            accounts.insertIfAbsent(candidate);
            FixtureRow actual = accounts.findByIdForUpdate(expected.id()).orElse(null);
            if (actual == null) continue;
            // A prior successful run may have needed a later probe because an earlier
            // candidate collided. Keep that stored candidate stable on every rerun.
            if (!actual.accountNumber().equals(number)) continue;
            verifyExpected(actual, candidate);
            return actual;
        }
        throw new AccountFixtureException("A unique synthetic account number could not be reserved safely.");
    }

    private static void verifyExpected(FixtureRow actual, FixtureRow expected) {
        if (!actual.customerId().equals(expected.customerId())
                || !actual.accountNumber().equals(expected.accountNumber())
                || !actual.accountType().equals(expected.accountType())
                || actual.balance().compareTo(expected.balance()) != 0
                || actual.primary() != expected.primary()
                || !actual.createdAt().equals(expected.createdAt())) {
            throw new AccountFixtureException("Existing fixture data does not match the expected account state.");
        }
    }

    private static FixtureRow expected(UUID customerId, AccountType type, String number) {
        UUID id = accountId(customerId, type);
        long customerSalt = Integer.toUnsignedLong(customerId.hashCode()) % 100_000;
        BigDecimal balance = BigDecimal.valueOf((type == AccountType.MAIN ? 12_500 : 4_750) + customerSalt, 0)
                .setScale(2);
        return new FixtureRow(id, customerId, number, type.name(), balance,
                type == AccountType.MAIN, type == AccountType.MAIN ? MAIN_CREATED_AT : SAVINGS_CREATED_AT);
    }

    private static UUID accountId(UUID customerId, AccountType type) {
        String key = "customer-accounts:" + customerId + ":" + type.name();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    static String syntheticNumber(UUID customerId, AccountType type, long probe) {
        String input = NUMBER_NAMESPACE + ":" + customerId + ":" + type.name() + ":" + probe;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            long value = new BigInteger(1, digest).mod(BigInteger.valueOf(NUMBER_SPACE)).longValueExact();
            return String.format(java.util.Locale.ROOT, "%0" + AccountNumberMask.ACCOUNT_NUMBER_LENGTH + "d", value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required digest algorithm is unavailable.", impossible);
        }
    }
}
