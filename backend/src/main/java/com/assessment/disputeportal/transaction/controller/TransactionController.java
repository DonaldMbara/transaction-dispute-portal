package com.assessment.disputeportal.transaction.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.security.activity.AuthenticatedActivity;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import com.assessment.disputeportal.transaction.mapper.TransactionResponseMapper;
import com.assessment.disputeportal.transaction.model.dto.TransactionPageResponse;
import com.assessment.disputeportal.transaction.service.TransactionQueryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public final class TransactionController {
    private final TransactionQueryParser parser;
    private final TransactionQueryService transactions;
    private final TransactionResponseMapper mapper;

    @GetMapping("/api/v1/transactions")
    @AuthenticatedActivity
    public ResponseEntity<ApiEnvelope> list(@AuthenticationPrincipal SessionPrincipal principal,
            HttpServletRequest request) {
        var query = parser.parse(queryParameters(request));
        var page = transactions.list(principal.accountId(), query);
        var response = new TransactionPageResponse(page.total(), page.page(), page.size(),
                page.items().stream().map(mapper::toResponse).toList());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiEnvelope.success(response));
    }

    @GetMapping("/api/v1/transactions/{id}")
    @AuthenticatedActivity
    public ResponseEntity<ApiEnvelope> get(@AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String id, HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) throw new InvalidRequestException("Transaction detail does not accept query parameters.");
        var transaction = transactions.get(principal.accountId(), parser.parsePathId(id));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiEnvelope.success(mapper.toResponse(transaction)));
    }

    private static Map<String, List<String>> queryParameters(HttpServletRequest request) {
        return request.getParameterMap().entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(Arrays.asList(entry.getValue()))));
    }
}
