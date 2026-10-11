package com.assessment.disputeportal.auth.model.dto;

import java.util.UUID;

import com.assessment.disputeportal.auth.model.AccountRole;

/** Credential-free identity returned by login and current-session lookup. */
public record IdentityResponse(UUID id, String email, AccountRole role) {}
