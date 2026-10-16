package com.assessment.disputeportal.transaction.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.assessment.disputeportal.transaction.model.projection.TransactionReadRow;
import com.assessment.disputeportal.transaction.model.query.TransactionQuery;

public interface TransactionReadRepository {
    boolean ownsAccount(UUID customerId, UUID accountId);
    long count(UUID customerId, TransactionQuery query);
    List<TransactionReadRow> list(UUID customerId, TransactionQuery query);
    Optional<TransactionReadRow> findOwned(UUID customerId, UUID transactionId);
}
