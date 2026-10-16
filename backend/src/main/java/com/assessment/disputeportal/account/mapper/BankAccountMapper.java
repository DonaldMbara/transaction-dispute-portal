package com.assessment.disputeportal.account.mapper;

import org.springframework.stereotype.Component;

import com.assessment.disputeportal.account.model.BankAccount;
import com.assessment.disputeportal.account.model.entity.BankAccountEntity;

@Component
public final class BankAccountMapper {
    public BankAccount toDomain(BankAccountEntity entity) {
        return new BankAccount(entity.getId(), entity.getCustomerId(), entity.getAccountNumber(),
                entity.getAccountType(), entity.getBalance(), entity.isPrimary(), entity.getCreatedAt());
    }
}