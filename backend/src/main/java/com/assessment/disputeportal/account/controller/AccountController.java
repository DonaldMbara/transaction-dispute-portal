package com.assessment.disputeportal.account.controller;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import com.assessment.disputeportal.account.mapper.AccountResponseMapper;
import com.assessment.disputeportal.account.model.dto.AccountPageResponse;
import com.assessment.disputeportal.account.service.AccountQueryService;
import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.security.activity.AuthenticatedActivity;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public final class AccountController {
    private static final Set<String> ALLOWED_PARAMETERS = Set.of("page", "size");
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final AccountQueryService accounts;
    private final AccountResponseMapper mapper;

    @GetMapping("/api/v1/accounts")
    @AuthenticatedActivity
    public ResponseEntity<ApiEnvelope> list(@AuthenticationPrincipal SessionPrincipal principal,
            HttpServletRequest request) {
        rejectUnknownParameters(request);
        int page = parameter(request, "page", DEFAULT_PAGE, Integer.MAX_VALUE);
        int size = parameter(request, "size", DEFAULT_SIZE, MAX_SIZE);
        var result = accounts.findOwnedAccounts(principal.accountId(), page, size);
        var data = new AccountPageResponse(result.total(), result.page(), result.size(),
                result.items().stream().map(mapper::toResponse).toList());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiEnvelope.success(data));
    }

    private static void rejectUnknownParameters(HttpServletRequest request) {
        if (request.getParameterMap().keySet().stream().anyMatch(key -> !ALLOWED_PARAMETERS.contains(key))) {
            throw new InvalidRequestException("Invalid account list parameters.");
        }
    }

    private static int parameter(HttpServletRequest request, String name, int defaultValue, int maximum) {
        String[] values = request.getParameterValues(name);
        if (values == null) return defaultValue;
        if (values.length != 1 || values[0] == null || !values[0].matches("[0-9]+")) {
            throw new InvalidRequestException("Invalid account list pagination.");
        }
        try {
            int value = Integer.parseInt(values[0]);
            if (value < 1 || value > maximum) throw new InvalidRequestException("Invalid account list pagination.");
            return value;
        } catch (NumberFormatException invalid) {
            throw new InvalidRequestException("Invalid account list pagination.");
        }
    }
}
