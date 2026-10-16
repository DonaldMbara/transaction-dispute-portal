package com.assessment.disputeportal.account.repository;

import com.assessment.disputeportal.account.mapper.BankAccountMapper;
import com.assessment.disputeportal.account.model.BankAccount;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
@RequiredArgsConstructor
class AccountReadRepositoryAdapter implements AccountReadRepository {
  private final JpaBankAccountRepository accounts;
  private final BankAccountMapper mapper;

  @Override
  public long countOwnedBy(UUID customerId) {
    return accounts.countByCustomerId(customerId);
  }

  @Override
  public List<BankAccount> findOwnedBy(UUID customerId, int page, int size) {
    return accounts.findOwnedAccounts(customerId, PageRequest.of(page - 1, size)).stream()
        .map(mapper::toDomain)
        .toList();
  }
}
