package com.assessment.disputeportal.account.mapper;

import com.assessment.disputeportal.account.model.BankAccount;
import com.assessment.disputeportal.account.model.dto.AccountResponse;
import java.math.RoundingMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public final class AccountResponseMapper {
  public AccountResponse toResponse(BankAccount account) {
    String rawNumber = account.accountNumber();
    boolean validNumber = AccountNumberMask.isWellFormed(rawNumber);
    if (!validNumber && AccountNumberMask.isMalformedPersistedValue(rawNumber)) {
      log.warn(
          "Malformed stored account number accountId={} reason={}",
          account.id(),
          AccountNumberMask.malformedReason(rawNumber));
    }
    return new AccountResponse(
        account.id(),
        account.accountType(),
        account.accountType().displayName(),
        validNumber ? rawNumber : null,
        AccountNumberMask.mask(rawNumber),
        account.balance().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
        "ZAR",
        account.primary());
  }
}
