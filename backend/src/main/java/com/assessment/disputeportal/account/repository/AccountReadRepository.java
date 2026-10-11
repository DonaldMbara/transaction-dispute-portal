package com.assessment.disputeportal.account.repository;

import java.util.List;
import java.util.UUID;

import com.assessment.disputeportal.account.model.BankAccount;

public interface AccountReadRepository {
    long countOwnedBy(UUID customerId);
    List<BankAccount> findOwnedBy(UUID customerId, int page, int size);
}
