package com.assessment.disputeportal.dispute.controller;

import java.util.UUID;
import java.util.regex.Pattern;

import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.dispute.model.dto.SubmitDisputeRequest;
import com.assessment.disputeportal.dispute.service.DisputeSubmissionService;
import com.assessment.disputeportal.security.activity.AuthenticatedActivity;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public final class DisputeSubmissionController {
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private final DisputeSubmissionService submissions;

    @PostMapping("/api/v1/disputes")
    @AuthenticatedActivity
    public ResponseEntity<Object> submit(@AuthenticationPrincipal SessionPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody SubmitDisputeRequest body) {
        if (body == null || body.hasUnknownFields() || body.getTransactionId() == null || body.getReason() == null
                || body.getDescription() == null || key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new InvalidRequestException("Invalid dispute submission.");
        }
        var result = submissions.submit(principal.accountId(), body.getTransactionId(), body.getReason(), body.getDescription(), key);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(java.net.URI.create("/api/v1/disputes/" + result.disputeId()))
                .cacheControl(CacheControl.noStore())
                .body(result.envelope());
    }
}
