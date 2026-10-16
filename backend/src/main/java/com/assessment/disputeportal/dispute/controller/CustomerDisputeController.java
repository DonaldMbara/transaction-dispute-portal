package com.assessment.disputeportal.dispute.controller;

import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.common.api.PageResponse;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.dispute.model.dto.CustomerDisputeDetailResponse;
import com.assessment.disputeportal.dispute.model.dto.CustomerDisputeListItemResponse;
import com.assessment.disputeportal.dispute.model.dto.DisputeSubmissionResponse;
import com.assessment.disputeportal.dispute.model.dto.SubmitDisputeRequest;
import com.assessment.disputeportal.dispute.model.query.DisputeQuery;
import com.assessment.disputeportal.dispute.service.DisputeQueryService;
import com.assessment.disputeportal.dispute.service.DisputeSubmissionService;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import com.assessment.disputeportal.security.web.AuthenticatedActivity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer-facing read-only dispute endpoints.
 *
 * <p>Customer identity is derived exclusively from the session principal; no user-supplied customer
 * or account identity is accepted as authorization.
 *
 * <p>Both handlers are marked {@link AuthenticatedActivity} to renew the strict idle deadline on
 * every authenticated, authorized CUSTOMER request.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/disputes")
public final class CustomerDisputeController {
  private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{1,128}");

  private final DisputeQueryParser queryParser;
  private final DisputeQueryService queryService;
  private final DisputeSubmissionService submissions;

  /**
   * GET /api/v1/disputes — paginated, ownership-scoped dispute list. Accepts: page, size,
   * accountId, status, reason, fromDate, toDate, search, sortBy, sortDirection.
   */
  @GetMapping
  @AuthenticatedActivity
  @Operation(
      parameters = {
        @Parameter(
            name = "page",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "integer", minimum = "1", defaultValue = "1")),
        @Parameter(
            name = "size",
            in = ParameterIn.QUERY,
            schema =
                @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20")),
        @Parameter(
            name = "accountId",
            in = ParameterIn.QUERY,
            description = "Optional owned account selector.",
            schema = @Schema(format = "uuid")),
        @Parameter(
            name = "status",
            in = ParameterIn.QUERY,
            schema =
                @Schema(
                    allowableValues = {
                      "ALL",
                      "ACTIVE",
                      "RESOLVED",
                      "OPEN",
                      "UNDER_REVIEW",
                      "ACCEPTED",
                      "REJECTED"
                    },
                    defaultValue = "ALL")),
        @Parameter(
            name = "reason",
            in = ParameterIn.QUERY,
            schema =
                @Schema(
                    allowableValues = {
                      "ALL",
                      "UNRECOGNIZED_TRANSACTION",
                      "DUPLICATE_CHARGE",
                      "INCORRECT_AMOUNT",
                      "GOODS_OR_SERVICES_NOT_RECEIVED",
                      "OTHER"
                    },
                    defaultValue = "ALL")),
        @Parameter(
            name = "fromDate",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string", format = "date")),
        @Parameter(
            name = "toDate",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string", format = "date")),
        @Parameter(
            name = "search",
            in = ParameterIn.QUERY,
            description =
                "Literal dispute, description, merchant or reference search, up to 100 characters.",
            schema = @Schema(maxLength = 100)),
        @Parameter(
            name = "sortBy",
            in = ParameterIn.QUERY,
            schema =
                @Schema(
                    allowableValues = {"createdAt", "updatedAt", "status"},
                    defaultValue = "createdAt")),
        @Parameter(
            name = "sortDirection",
            in = ParameterIn.QUERY,
            schema =
                @Schema(
                    allowableValues = {"ASC", "DESC"},
                    defaultValue = "DESC"))
      })
  public ResponseEntity<ApiEnvelope<PageResponse<CustomerDisputeListItemResponse>>> listDisputes(
      @AuthenticationPrincipal SessionPrincipal principal, HttpServletRequest request) {
    UUID customerId = principal.accountId();
    Map<String, String[]> parameterMap = request.getParameterMap();
    DisputeQuery query = queryParser.parse(parameterMap, customerId);
    Page<CustomerDisputeListItemResponse> page = queryService.getCustomerDisputePage(query);
    var pageResponse =
        new PageResponse<>(
            page.getTotalElements(),
            query.pageRequest().getPageNumber() + 1,
            query.pageRequest().getPageSize(),
            page.getContent());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiEnvelope.success(pageResponse));
  }

  /**
   * GET /api/v1/disputes/{id} — single owned dispute detail with curated timeline. Rejects any
   * query parameters (they are unknown to this endpoint).
   */
  @GetMapping("/{id}")
  @AuthenticatedActivity
  public ResponseEntity<ApiEnvelope<CustomerDisputeDetailResponse>> getDispute(
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
    CustomerDisputeDetailResponse detail =
        queryService.getCustomerDisputeDetail(disputeId, customerId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiEnvelope.success(detail));
  }

  @PostMapping
  @ApiResponse(responseCode = "201", description = "Dispute created or successful replay.")
  @AuthenticatedActivity
  public ResponseEntity<ApiEnvelope<DisputeSubmissionResponse>> submit(
      @AuthenticationPrincipal SessionPrincipal principal,
      @Parameter(
              name = "Idempotency-Key",
              in = ParameterIn.HEADER,
              required = true,
              description = "Required idempotency key for safe submission replay.",
              schema = @Schema(pattern = "[A-Za-z0-9_-]{1,128}", maxLength = 128))
          @RequestHeader(value = "Idempotency-Key", required = false)
          String key,
      @Valid @RequestBody SubmitDisputeRequest body) {
    if (key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
      throw new InvalidRequestException("Invalid dispute submission.");
    }
    var result =
        submissions.submit(
            principal.accountId(), body.transactionId(), body.reason(), body.description(), key);
    return ResponseEntity.status(HttpStatus.CREATED)
        .location(java.net.URI.create("/api/v1/disputes/" + result.disputeId()))
        .cacheControl(CacheControl.noStore())
        .body(ApiEnvelope.success(result.data()));
  }
}
