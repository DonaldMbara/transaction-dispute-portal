package com.assessment.disputeportal.account.repository;

import java.util.List;
import java.util.UUID;

import com.assessment.disputeportal.account.model.entity.BankAccountEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaBankAccountRepository extends JpaRepository<BankAccountEntity, UUID> {
    long countByCustomerId(UUID customerId);

    @Query("select account from BankAccountEntity account where account.customerId = :customerId "
            + "order by account.isPrimary desc, account.createdAt asc, account.id asc")
    List<BankAccountEntity> findOwnedAccounts(@Param("customerId") UUID customerId, Pageable pageable);
}
