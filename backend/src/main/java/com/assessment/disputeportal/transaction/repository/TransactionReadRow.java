package com.assessment.disputeportal.transaction.repository;

import com.assessment.disputeportal.transaction.model.BankTransaction;

public record TransactionReadRow(BankTransaction transaction, String accountNumber) {}
