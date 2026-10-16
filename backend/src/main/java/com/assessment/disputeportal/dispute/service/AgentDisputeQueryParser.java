package com.assessment.disputeportal.dispute.service;

import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.dispute.model.DisputeReason;
import com.assessment.disputeportal.dispute.model.query.AgentDisputeQuery;
import com.assessment.disputeportal.dispute.model.AgentQueue;
import com.assessment.disputeportal.dispute.model.query.DisputeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AgentDisputeQueryParser {
    private final DisputeQueryParser sharedFilters;

    public AgentDisputeQuery parse(Map<String, String[]> parameters, UUID agentId) {
        String[] values = parameters.get("queue");
        if (values != null && values.length != 1) throw new InvalidRequestException("Duplicate query parameter: queue");
        AgentQueue queue = values == null || values[0] == null || values[0].isBlank()
                ? AgentQueue.UNASSIGNED : parseQueue(values[0]);
        validateReason(parameters);
        Map<String, String[]> common = new LinkedHashMap<>(parameters);
        common.remove("queue");
        DisputeQuery filters = sharedFilters.parse(common, agentId);
        return new AgentDisputeQuery(agentId, queue, filters);
    }

    private void validateReason(Map<String, String[]> parameters) {
        String[] values = parameters.get("reason");
        if (values == null || values.length == 0 || values[0] == null || values[0].isBlank()
                || "ALL".equalsIgnoreCase(values[0])) return;
        try { DisputeReason.valueOf(values[0].toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException failure) { throw new InvalidRequestException("Unknown reason value."); }
    }

    private AgentQueue parseQueue(String raw) {
        return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
            case "unassigned" -> AgentQueue.UNASSIGNED;
            case "mine" -> AgentQueue.MINE;
            case "all" -> AgentQueue.ALL;
            default -> throw new InvalidRequestException("Unknown queue value.");
        };
    }
}
