package com.assessment.disputeportal.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.assessment.disputeportal.account.mapper.AccountResponseMapper;
import com.assessment.disputeportal.account.model.AccountType;
import com.assessment.disputeportal.account.model.BankAccount;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountResponseMapperTest {
    private final AccountResponseMapper mapper = new AccountResponseMapper();

    @Test
    void disclosesOnlyWellFormedNumberAndNeverReturnsMalformedRawValue() {
        UUID id = UUID.randomUUID();
        var valid = mapper.toResponse(new BankAccount(id, UUID.randomUUID(), "0123456789", AccountType.MAIN,
                new BigDecimal("12500.00"), true, Instant.parse("2025-01-01T00:00:00Z")));
        assertThat(valid.accountNumber()).isEqualTo("0123456789");
        assertThat(valid.maskedNumber()).isEqualTo("****6789");
        assertThat(valid.balance()).isEqualTo("12500.00");

        String malformed = "12 4567890";
        var invalid = mapper.toResponse(new BankAccount(id, UUID.randomUUID(), malformed, AccountType.SAVINGS,
                new BigDecimal("1.00"), false, Instant.parse("2025-01-02T00:00:00Z")));
        assertThat(invalid.accountNumber()).isNull();
        assertThat(invalid.maskedNumber()).isEqualTo("****");
        assertThat(invalid.toString()).doesNotContain(malformed);

        var missing = mapper.toResponse(new BankAccount(id, UUID.randomUUID(), null, AccountType.MAIN,
                new BigDecimal("1.00"), false, Instant.parse("2025-01-02T00:00:00Z")));
        assertThat(missing.accountNumber()).isNull();
        assertThat(missing.maskedNumber()).isEqualTo("****");
    }
}
