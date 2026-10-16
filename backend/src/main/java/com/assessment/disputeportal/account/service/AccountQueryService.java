package com.assessment.disputeportal.account.service;

import com.assessment.disputeportal.account.model.BankAccount;
import com.assessment.disputeportal.account.repository.AccountReadRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountQueryService {
  private final AccountReadRepository accounts;

  @Transactional(readOnly = true)
  public AccountPage findOwnedAccounts(UUID customerId, int page, int size) {
    long total = accounts.countOwnedBy(customerId);
    List<BankAccount> items = accounts.findOwnedBy(customerId, page, size);
    return new AccountPage(total, page, size, items);
  }

  public record AccountPage(long total, int page, int size, List<BankAccount> items) {
    public AccountPage {
      items = List.copyOf(items);
    }
  }
}
