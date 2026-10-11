package com.assessment.disputeportal.transaction.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.assessment.disputeportal.transaction.model.BankTransaction;
import com.assessment.disputeportal.transaction.model.TransactionDirection;
import com.assessment.disputeportal.transaction.model.TransactionStatus;
import com.assessment.disputeportal.transaction.repository.TransactionReadRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TransactionResponseMapperTest {
    private final TransactionResponseMapper mapper = new TransactionResponseMapper();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializedTransactionAndDisputeShapeContainsOnlyTheSharedMask() throws Exception {
        var response = mapper.toResponse(row("0123456789", TransactionStatus.COMPLETED));
        String serialized = json.writeValueAsString(response);
        assertThat(serialized).contains("****6789").doesNotContain("0123456789").doesNotContain("accountNumber");
        assertThat(response.dispute().id()).isNull();
        assertThat(response.dispute().eligible()).isTrue();
        assertThat(response.dispute().ineligibilityReason()).isNull();
    }

    @Test
    void malformedLegacyAccountNumberIsMaskedAndEligibilityDependsOnlyOnStatus() {
        var response = mapper.toResponse(row("bad-value", TransactionStatus.PENDING));
        assertThat(response.account().maskedNumber()).isEqualTo("****");
        assertThat(response.dispute().eligible()).isFalse();
        assertThat(response.dispute().ineligibilityReason()).isEqualTo("NOT_COMPLETED");
    }

    private static TransactionReadRow row(String number, TransactionStatus status) {
        UUID accountId = UUID.randomUUID();
        var transaction = new BankTransaction(UUID.randomUUID(), accountId, "TXN-TEST", "Synthetic Shop",
                new BigDecimal("12.30"), "ZAR", TransactionDirection.OUT, status,
                Instant.parse("2025-03-10T08:00:00Z"));
        return new TransactionReadRow(transaction, number);
    }
}
