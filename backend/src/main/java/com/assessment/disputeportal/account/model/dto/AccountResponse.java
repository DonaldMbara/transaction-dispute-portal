package com.assessment.disputeportal.account.model.dto;

import com.assessment.disputeportal.account.model.AccountType;
import java.util.UUID;

public record AccountResponse(
    UUID id,
    AccountType accountType,
    String displayName,
    String accountNumber,
    String maskedNumber,
    String balance,
    String currency,
    boolean primary) {}
