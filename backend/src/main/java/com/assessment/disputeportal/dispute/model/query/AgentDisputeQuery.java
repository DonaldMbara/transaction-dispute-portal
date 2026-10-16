package com.assessment.disputeportal.dispute.model.query;

import com.assessment.disputeportal.dispute.model.AgentQueue;
import java.util.UUID;

public record AgentDisputeQuery(UUID agentId, AgentQueue queue, DisputeQuery filters) {}
