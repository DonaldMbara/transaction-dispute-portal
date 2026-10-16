package com.assessment.disputeportal.account.repository;

import com.assessment.disputeportal.account.model.BankAccount;
import java.util.List;
import java.util.UUID;

public interface AccountReadRepository {
  long countOwnedBy(UUID customerId);

  List<BankAccount> findOwnedBy(UUID customerId, int page, int size);
}
