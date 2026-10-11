package com.assessment.disputeportal.transaction.repository;

import com.assessment.disputeportal.transaction.model.BankTransaction;
import java.util.UUID;

public record TransactionReadRow(BankTransaction transaction, String accountNumber, UUID disputeId) {}
