package com.assessment.disputeportal.dispute.controller;

import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.common.api.PageResponse;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.dispute.model.dto.AgentDisputeDetailResponse;
import com.assessment.disputeportal.dispute.model.dto.AgentDisputeListItemResponse;
import com.assessment.disputeportal.dispute.model.dto.ClaimDisputeRequest;
import com.assessment.disputeportal.dispute.model.dto.ReleaseDisputeRequest;
import com.assessment.disputeportal.dispute.model.dto.ResolveDisputeRequest;
import com.assessment.disputeportal.dispute.model.query.AgentDisputeQuery;
import com.assessment.disputeportal.dispute.service.AgentDisputeCommandService;
import com.assessment.disputeportal.dispute.service.AgentDisputeQueryService;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import com.assessment.disputeportal.security.web.AuthenticatedActivity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent/disputes")
public final class AgentDisputeController {
  private final AgentDisputeQueryParser parser;
  private final AgentDisputeQueryService service;
  private final AgentDisputeCommandService commands;

  @GetMapping
  @AuthenticatedActivity
  @Operation(
      parameters = {
        @Parameter(
            name = "queue",
            in = ParameterIn.QUERY,
            schema =
                @Schema(
                    allowableValues = {"unassigned", "mine", "all"},
                    defaultValue = "unassigned")),
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
  public ResponseEntity<ApiEnvelope<PageResponse<AgentDisputeListItemResponse>>> list(
      @AuthenticationPrincipal SessionPrincipal principal, HttpServletRequest request) {
    AgentDisputeQuery query = parser.parse(request.getParameterMap(), principal.accountId());
    Page<AgentDisputeListItemResponse> page = service.list(query);
    var response =
        new PageResponse<>(
            page.getTotalElements(),
            query.filters().pageRequest().getPageNumber() + 1,
            query.filters().pageRequest().getPageSize(),
            page.getContent());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiEnvelope.success(response));
  }

  @GetMapping("/{id}")
  @AuthenticatedActivity
  public ResponseEntity<ApiEnvelope<AgentDisputeDetailResponse>> detail(
      @AuthenticationPrincipal SessionPrincipal principal,
      @PathVariable String id,
      HttpServletRequest request) {
    if (!request.getParameterMap().isEmpty())
      throw new InvalidRequestException("Unknown query parameters.");
    UUID disputeId;
    try {
      disputeId = UUID.fromString(id);
    } catch (IllegalArgumentException failure) {
      throw new InvalidRequestException("Dispute ID must be a valid UUID.");
    }
    AgentDisputeDetailResponse detail = service.detail(disputeId);
    String etag = AgentIfMatch.format(detail.id(), detail.version());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.ETAG, etag)
        .body(ApiEnvelope.success(detail));
  }

  @PostMapping("/{id}/claim")
  @Operation(
      parameters =
          @Parameter(
              name = "If-Match",
              in = ParameterIn.HEADER,
              required = true,
              description = "Strong ETag from the parent dispute detail response.",
              schema = @Schema(pattern = "\"dispute-[0-9a-f-]+-v[0-9]+\"")))
  @AuthenticatedActivity
  public ResponseEntity<ApiEnvelope<AgentDisputeDetailResponse>> claim(
      @AuthenticationPrincipal SessionPrincipal principal,
      @PathVariable String id,
      @Valid @RequestBody(required = false) ClaimDisputeRequest body,
      HttpServletRequest request) {
    AgentIfMatch precondition = ifMatch(request);
    AgentDisputeDetailResponse result =
        commands.claim(
            parseId(id), principal.accountId(), precondition.disputeId(), precondition.version());
    return response(result);
  }

  @PostMapping("/{id}/release")
  @Operation(
      parameters =
          @Parameter(
              name = "If-Match",
              in = ParameterIn.HEADER,
              required = true,
              description = "Strong ETag from the parent dispute detail response.",
              schema = @Schema(pattern = "\"dispute-[0-9a-f-]+-v[0-9]+\"")))
  @AuthenticatedActivity
  public ResponseEntity<ApiEnvelope<AgentDisputeDetailResponse>> release(
      @AuthenticationPrincipal SessionPrincipal principal,
      @PathVariable String id,
      @Valid @RequestBody ReleaseDisputeRequest body,
      HttpServletRequest request) {
    AgentIfMatch precondition = ifMatch(request);
    return response(
        commands.release(
            parseId(id),
            principal.accountId(),
            precondition.disputeId(),
            precondition.version(),
            body.reason()));
  }

  @PostMapping("/{id}/resolve")
  @Operation(
      parameters =
          @Parameter(
              name = "If-Match",
              in = ParameterIn.HEADER,
              required = true,
              description = "Strong ETag from the parent dispute detail response.",
              schema = @Schema(pattern = "\"dispute-[0-9a-f-]+-v[0-9]+\"")))
  @AuthenticatedActivity
  public ResponseEntity<ApiEnvelope<AgentDisputeDetailResponse>> resolve(
      @AuthenticationPrincipal SessionPrincipal principal,
      @PathVariable String id,
      @Valid @RequestBody ResolveDisputeRequest body,
      HttpServletRequest request) {
    AgentIfMatch precondition = ifMatch(request);
    return response(
        commands.resolve(
            parseId(id),
            principal.accountId(),
            precondition.disputeId(),
            precondition.version(),
            body.decision(),
            body.internalResolutionNotes(),
            body.customerResolutionMessage()));
  }

  private ResponseEntity<ApiEnvelope<AgentDisputeDetailResponse>> response(
      AgentDisputeDetailResponse detail) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.ETAG, AgentIfMatch.format(detail.id(), detail.version()))
        .body(ApiEnvelope.success(detail));
  }

  private static UUID parseId(String id) {
    try {
      return UUID.fromString(id);
    } catch (IllegalArgumentException failure) {
      throw new InvalidRequestException("Dispute ID must be a valid UUID.");
    }
  }

  private static AgentIfMatch ifMatch(HttpServletRequest request) {
    List<String> headers = new ArrayList<>();
    var values = request.getHeaders(HttpHeaders.IF_MATCH);
    while (values.hasMoreElements()) headers.add(values.nextElement());
    return AgentIfMatch.parse(headers);
  }
}
