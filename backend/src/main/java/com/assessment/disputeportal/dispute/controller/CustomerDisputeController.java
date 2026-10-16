package com.assessment.disputeportal.dispute.controller;

import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.dispute.model.dto.CustomerDisputeDetailDto;
import com.assessment.disputeportal.dispute.model.dto.CustomerDisputeListItemDto;
import com.assessment.disputeportal.dispute.model.query.DisputeQuery;
import com.assessment.disputeportal.dispute.service.DisputeQueryParser;
import com.assessment.disputeportal.dispute.service.DisputeQueryService;
import com.assessment.disputeportal.security.activity.AuthenticatedActivity;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Customer-facing read-only dispute endpoints.
 *
 * <p>Customer identity is derived exclusively from the session principal; no user-supplied
 * customer or account identity is accepted as authorization.</p>
 *
 * <p>Both handlers are marked {@link AuthenticatedActivity} to renew the strict idle deadline
 * on every authenticated, authorized CUSTOMER request.</p>
 */
@RestController
@RequiredArgsConstructor
public final class CustomerDisputeController {

    private final DisputeQueryParser queryParser;
    private final DisputeQueryService queryService;

    /**
     * GET /api/v1/disputes — paginated, ownership-scoped dispute list.
     * Accepts: page, size, accountId, status, reason, fromDate, toDate, search, sortBy, sortDirection.
     */
    @GetMapping("/api/v1/disputes")
    @AuthenticatedActivity
    public ResponseEntity<ApiEnvelope> listDisputes(
            @AuthenticationPrincipal SessionPrincipal principal,
            HttpServletRequest request) {
        UUID customerId = principal.accountId();
        Map<String, String[]> parameterMap = request.getParameterMap();
        DisputeQuery query = queryParser.parse(parameterMap, customerId);
        Page<CustomerDisputeListItemDto> page = queryService.getCustomerDisputePage(query);
        var pageResponse = new DisputePageResponse(page.getTotalElements(), query.pageRequest().getPageNumber() + 1,
                query.pageRequest().getPageSize(), page.getContent());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiEnvelope.success(pageResponse));
    }

    /**
     * GET /api/v1/disputes/{id} — single owned dispute detail with curated timeline.
     * Rejects any query parameters (they are unknown to this endpoint).
     */
    @GetMapping("/api/v1/disputes/{id}")
    @AuthenticatedActivity
    public ResponseEntity<ApiEnvelope> getDispute(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String id,
            HttpServletRequest request) {
        // Reject any query parameters — the detail endpoint accepts none
        if (!request.getParameterMap().isEmpty()) {
            throw new InvalidRequestException("Unknown query parameters on dispute detail endpoint.");
        }
        UUID disputeId;
        try {
            disputeId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Dispute ID must be a valid UUID.");
        }
        UUID customerId = principal.accountId();
        CustomerDisputeDetailDto detail = queryService.getCustomerDisputeDetail(disputeId, customerId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiEnvelope.success(detail));
    }

    /**
     * Page response wrapper matching the standard collection envelope shape.
     */
    private record DisputePageResponse(long total, int page, int size, java.util.List<CustomerDisputeListItemDto> items) {}
}
