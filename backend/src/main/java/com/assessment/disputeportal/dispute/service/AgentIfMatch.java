package com.assessment.disputeportal.dispute.service;

import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.dispute.exception.DisputeApiException;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record AgentIfMatch(UUID disputeId, long version) {
    private static final Pattern TAG = Pattern.compile("^\\\"dispute-([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})-v(0|[1-9][0-9]*)\\\"$");

    static AgentIfMatch parse(List<String> values) {
        if (values == null || values.isEmpty()) throw DisputeApiException.preconditionRequired();
        if (values.size() != 1) throw new InvalidRequestException("Exactly one If-Match tag is required.");
        String value = values.get(0) == null ? "" : values.get(0).trim();
        Matcher matcher = TAG.matcher(value);
        if (!matcher.matches()) throw new InvalidRequestException("If-Match tag is malformed.");
        try {
            return new AgentIfMatch(UUID.fromString(matcher.group(1)), Long.parseLong(matcher.group(2)));
        } catch (IllegalArgumentException failure) {
            throw new InvalidRequestException("If-Match tag is malformed.");
        }
    }
}
