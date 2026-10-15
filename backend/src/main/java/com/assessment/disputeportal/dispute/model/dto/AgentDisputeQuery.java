package com.assessment.disputeportal.dispute.model.dto;

import java.util.UUID;

public record AgentDisputeQuery(UUID agentId, AgentQueue queue, DisputeQuery filters) {}
