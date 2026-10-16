package com.assessment.disputeportal.dispute.controller;

import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.dispute.model.dto.AgentDisputeDetailDto;
import com.assessment.disputeportal.dispute.model.dto.AgentDisputeListItemDto;
import com.assessment.disputeportal.dispute.model.query.AgentDisputeQuery;
import com.assessment.disputeportal.dispute.service.AgentDisputeQueryService;
import com.assessment.disputeportal.security.activity.AuthenticatedActivity;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public final class AgentDisputeController {
    private final AgentDisputeQueryParser parser;
    private final AgentDisputeQueryService service;

    @GetMapping("/api/v1/agent/disputes")
    @AuthenticatedActivity
    public ResponseEntity<ApiEnvelope> list(@AuthenticationPrincipal SessionPrincipal principal,
            HttpServletRequest request) {
        AgentDisputeQuery query = parser.parse(request.getParameterMap(), principal.accountId());
        Page<AgentDisputeListItemDto> page = service.list(query);
        var response = new PageResponse(page.getTotalElements(), query.filters().pageRequest().getPageNumber() + 1,
                query.filters().pageRequest().getPageSize(), page.getContent());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiEnvelope.success(response));
    }

    @GetMapping("/api/v1/agent/disputes/{id}")
    @AuthenticatedActivity
    public ResponseEntity<ApiEnvelope> detail(@AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String id, HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) throw new InvalidRequestException("Unknown query parameters.");
        UUID disputeId;
        try { disputeId = UUID.fromString(id); }
        catch (IllegalArgumentException failure) { throw new InvalidRequestException("Dispute ID must be a valid UUID."); }
        AgentDisputeDetailDto detail = service.detail(disputeId);
        String etag = AgentIfMatch.format(detail.id(), detail.version());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.ETAG, etag)
                .body(ApiEnvelope.success(detail));
    }

    private record PageResponse(long total, int page, int size, List<AgentDisputeListItemDto> items) {}
}
