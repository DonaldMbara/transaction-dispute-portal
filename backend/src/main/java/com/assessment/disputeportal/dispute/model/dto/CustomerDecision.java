package com.assessment.disputeportal.dispute.model.dto;

/** Customer-facing dispute resolution outcome. Null until the dispute is terminal. */
public record CustomerDecision(String outcome, String message) {}
