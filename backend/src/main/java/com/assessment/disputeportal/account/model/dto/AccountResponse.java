package com.assessment.disputeportal.account.model.dto;

import java.util.UUID;

import com.assessment.disputeportal.account.model.AccountType;

public record AccountResponse(UUID id, AccountType accountType, String displayName, String accountNumber,
        String maskedNumber, String balance, String currency, boolean primary) {}
