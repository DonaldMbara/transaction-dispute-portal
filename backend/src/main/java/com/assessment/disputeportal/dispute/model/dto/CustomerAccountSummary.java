package com.assessment.disputeportal.dispute.model.dto;

import java.util.UUID;

/**
 * Customer-safe account context embedded in dispute responses. Never exposes raw account number.
 */
public record CustomerAccountSummary(UUID id, String displayName, String maskedNumber) {}
