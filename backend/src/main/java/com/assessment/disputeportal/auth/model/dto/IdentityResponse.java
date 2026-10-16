package com.assessment.disputeportal.auth.model.dto;

import com.assessment.disputeportal.auth.model.AccountRole;
import java.util.UUID;

/** Credential-free identity returned by login and current-session lookup. */
public record IdentityResponse(UUID id, String email, AccountRole role) {}
