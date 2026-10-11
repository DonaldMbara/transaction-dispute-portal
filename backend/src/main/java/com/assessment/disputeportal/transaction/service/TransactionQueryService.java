package com.assessment.disputeportal.transaction.service;

import java.util.UUID;

import com.assessment.disputeportal.common.exception.ResourceNotFoundException;
import com.assessment.disputeportal.transaction.model.TransactionPage;
import com.assessment.disputeportal.transaction.model.TransactionQuery;
import com.assessment.disputeportal.transaction.repository.TransactionReadRepository;
import com.assessment.disputeportal.transaction.repository.TransactionReadRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionQueryService {
    private final TransactionReadRepository transactions;

    @Transactional(readOnly = true)
    public TransactionPage list(UUID customerId, TransactionQuery query) {
        if (query.accountId() != null && !transactions.ownsAccount(customerId, query.accountId())) {
            throw new ResourceNotFoundException("Account", "id", query.accountId());
        }
        long total = transactions.count(customerId, query);
        return new TransactionPage(total, query.page(), query.size(), transactions.list(customerId, query));
    }

    @Transactional(readOnly = true)
    public TransactionReadRow get(UUID customerId, UUID transactionId) {
        return transactions.findOwned(customerId, transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
    }
}
