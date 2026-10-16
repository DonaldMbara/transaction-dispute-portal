package com.assessment.disputeportal.auth.mapper;

import com.assessment.disputeportal.auth.model.Account;
import com.assessment.disputeportal.auth.model.entity.AppUser;
import org.springframework.stereotype.Component;

@Component
public final class AccountMapper {

  public Account toDomain(AppUser entity) {
    return new Account(
        entity.getId(),
        entity.getEmail(),
        entity.getPasswordHash(),
        entity.getRole(),
        entity.isEnabled(),
        entity.getCreatedAt(),
        entity.getAuthVersion());
  }

  public AppUser toEntity(Account account) {
    AppUser entity = new AppUser();
    updateEntity(account, entity);
    return entity;
  }

  public void updateEntity(Account account, AppUser entity) {
    entity.setId(account.id());
    entity.setEmail(account.email());
    entity.setPasswordHash(account.passwordHash());
    entity.setRole(account.role());
    entity.setEnabled(account.enabled());
    entity.setCreatedAt(account.createdAt());
    entity.setAuthVersion(account.authVersion());
  }
}
