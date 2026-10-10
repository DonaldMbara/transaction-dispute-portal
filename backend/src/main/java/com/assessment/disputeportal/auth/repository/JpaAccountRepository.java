package com.assessment.disputeportal.auth.repository;

import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.assessment.disputeportal.auth.mapper.AccountMapper;
import com.assessment.disputeportal.auth.model.Account;
import com.assessment.disputeportal.common.exception.ResourceNotFoundException;

@Repository
@RequiredArgsConstructor
public class JpaAccountRepository implements AccountRepository {

    private final AppUserRepository users;
    private final AccountMapper mapper;

    @Override
    public void insert(Account account) {
        users.saveAndFlush(mapper.toEntity(account));
    }

    @Override
    public Optional<Account> findByIdForUpdate(UUID id) {
        return users.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public void save(Account account) {
        var entity = users.findById(account.id())
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", account.id()));
        mapper.updateEntity(account, entity);
        users.flush();
    }
}