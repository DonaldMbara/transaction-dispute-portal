package com.assessment.disputeportal.transaction.model.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.assessment.disputeportal.transaction.model.TransactionDirection;
import com.assessment.disputeportal.transaction.model.TransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bank_transactions")
@Getter
@NoArgsConstructor
public class BankTransactionEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(nullable = false, unique = true, length = 80, updatable = false)
    private String reference;

    @Column(nullable = false, length = 160, updatable = false)
    private String merchant;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)", updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8, updatable = false)
    private TransactionDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private TransactionStatus status;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}