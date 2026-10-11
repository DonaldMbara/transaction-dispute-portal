package com.assessment.disputeportal.auth.model.dto;

/** Masked CSRF bootstrap payload for the Angular client. */
public record CsrfBootstrapResponse(String csrfToken, String headerName) {}
