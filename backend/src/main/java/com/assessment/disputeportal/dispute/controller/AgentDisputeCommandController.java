package com.assessment.disputeportal.dispute.controller;

import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.dispute.model.dto.AgentDisputeDetailDto;
import com.assessment.disputeportal.dispute.service.AgentDisputeCommandService;
import com.assessment.disputeportal.security.web.AuthenticatedActivity;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public final class AgentDisputeCommandController {
    private final AgentDisputeCommandService commands;

    @PostMapping("/api/v1/agent/disputes/{id}/claim")
    @AuthenticatedActivity
    public ResponseEntity<ApiEnvelope> claim(@AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String id, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        if (body != null && (!body.isObject() || body.size() != 0)) {
            throw new InvalidRequestException("Claim command accepts an empty object only.");
        }
        AgentIfMatch precondition = ifMatch(request);
        AgentDisputeDetailDto result = commands.claim(parseId(id), principal.accountId(),
                precondition.disputeId(), precondition.version());
        return response(result);
    }

    @PostMapping("/api/v1/agent/disputes/{id}/release")
    @AuthenticatedActivity
    public ResponseEntity<ApiEnvelope> release(@AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String id, @RequestBody JsonNode body, HttpServletRequest request) {
        requireFields(body, Set.of("reason"));
        String reason = text(body, "reason");
        AgentIfMatch precondition = ifMatch(request);
        return response(commands.release(parseId(id), principal.accountId(),
            precondition.disputeId(), precondition.version(), reason));
    }

    @PostMapping("/api/v1/agent/disputes/{id}/resolve")
    @AuthenticatedActivity
    public ResponseEntity<ApiEnvelope> resolve(@AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String id, @RequestBody JsonNode body, HttpServletRequest request) {
        requireFields(body, Set.of("decision", "internalResolutionNotes", "customerResolutionMessage"));
        String decision = text(body, "decision");
        String internal = text(body, "internalResolutionNotes");
        String customer = text(body, "customerResolutionMessage");
        AgentIfMatch precondition = ifMatch(request);
        return response(commands.resolve(parseId(id), principal.accountId(),
            precondition.disputeId(), precondition.version(), decision, internal, customer));
    }

    private ResponseEntity<ApiEnvelope> response(AgentDisputeDetailDto detail) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.ETAG,
            AgentIfMatch.format(detail.id(), detail.version()))
                .body(ApiEnvelope.success(detail));
    }

    private static UUID parseId(String id) {
        try { return UUID.fromString(id); }
        catch (IllegalArgumentException failure) { throw new InvalidRequestException("Dispute ID must be a valid UUID."); }
    }

    private static AgentIfMatch ifMatch(HttpServletRequest request) {
        List<String> headers = new ArrayList<>();
        var values = request.getHeaders(HttpHeaders.IF_MATCH);
        while (values.hasMoreElements()) headers.add(values.nextElement());
        return AgentIfMatch.parse(headers);
    }

    private static void requireFields(JsonNode body, Set<String> expected) {
        if (body == null || !body.isObject()) throw new InvalidRequestException("A JSON object is required.");
        Set<String> actual = new HashSet<>();
        body.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw new InvalidRequestException("Request fields do not match the command contract.");
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual()) throw new InvalidRequestException("Required text field is missing.");
        return value.textValue();
    }
}
