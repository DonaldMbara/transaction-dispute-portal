package com.assessment.disputeportal.transaction.mapper;

import com.assessment.disputeportal.account.AccountNumberMask;
import com.assessment.disputeportal.transaction.model.TransactionStatus;
import com.assessment.disputeportal.transaction.model.dto.TransactionResponse;
import com.assessment.disputeportal.transaction.repository.TransactionReadRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public final class TransactionResponseMapper {
    public TransactionResponse toResponse(TransactionReadRow row) {
        var transaction = row.transaction();
        String number = row.accountNumber();
        if (AccountNumberMask.isMalformedPersistedValue(number)) {
            log.warn("Malformed stored account number accountId={} reason={}", transaction.accountId(),
                    AccountNumberMask.malformedReason(number));
        }
        boolean eligible = transaction.status() == TransactionStatus.COMPLETED;
        var dispute = new TransactionResponse.DisputeEligibility(null, eligible, eligible ? null : "NOT_COMPLETED");
        return new TransactionResponse(transaction.id(), transaction.reference(), transaction.merchant(),
                transaction.amount().toPlainString(), transaction.currency(), transaction.direction(), transaction.status(),
                transaction.occurredAt(), new TransactionResponse.AccountSummary(transaction.accountId(), AccountNumberMask.mask(number)),
                dispute);
    }
}
