package com.assessment.disputeportal.auth.model;

import java.util.UUID;

/** Internal account-status projection, not the future HTTP identity contract. */
public record AccountIdentity(
    UUID id, String email, AccountRole role, boolean enabled, long authVersion) {}
