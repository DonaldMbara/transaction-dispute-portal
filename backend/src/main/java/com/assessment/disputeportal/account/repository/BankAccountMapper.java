package com.assessment.disputeportal.account.repository;

import org.springframework.stereotype.Component;

import com.assessment.disputeportal.account.model.BankAccount;
import com.assessment.disputeportal.account.model.entity.BankAccountEntity;

@Component
final class BankAccountMapper {
    BankAccount toDomain(BankAccountEntity entity) {
        return new BankAccount(entity.getId(), entity.getCustomerId(), entity.getAccountNumber(),
                entity.getAccountType(), entity.getBalance(), entity.isPrimary(), entity.getCreatedAt());
    }
}
