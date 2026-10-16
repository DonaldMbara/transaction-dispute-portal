package com.assessment.disputeportal.dispute.controller;

import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.common.api.PageResponse;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.dispute.model.dto.AgentDisputeDetailDto;
import com.assessment.disputeportal.dispute.model.dto.AgentDisputeListItemDto;
import com.assessment.disputeportal.dispute.model.query.AgentDisputeQuery;
import com.assessment.disputeportal.dispute.service.AgentDisputeCommandService;
import com.assessment.disputeportal.dispute.service.AgentDisputeQueryService;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import com.assessment.disputeportal.security.web.AuthenticatedActivity;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
  public ResponseEntity<ApiEnvelope<?>> list(
      @AuthenticationPrincipal SessionPrincipal principal, HttpServletRequest request) {
    AgentDisputeQuery query = parser.parse(request.getParameterMap(), principal.accountId());
    Page<AgentDisputeListItemDto> page = service.list(query);
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
  public ResponseEntity<ApiEnvelope<?>> detail(
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
    AgentDisputeDetailDto detail = service.detail(disputeId);
    String etag = AgentIfMatch.format(detail.id(), detail.version());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.ETAG, etag)
        .body(ApiEnvelope.success(detail));
  }

  @PostMapping("/{id}/claim")
  @AuthenticatedActivity
  public ResponseEntity<ApiEnvelope<?>> claim(
      @AuthenticationPrincipal SessionPrincipal principal,
      @PathVariable String id,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    if (body != null && (!body.isObject() || body.size() != 0)) {
      throw new InvalidRequestException("Claim command accepts an empty object only.");
    }
    AgentIfMatch precondition = ifMatch(request);
    AgentDisputeDetailDto result =
        commands.claim(
            parseId(id), principal.accountId(), precondition.disputeId(), precondition.version());
    return response(result);
  }

  @PostMapping("/{id}/release")
  @AuthenticatedActivity
  public ResponseEntity<ApiEnvelope<?>> release(
      @AuthenticationPrincipal SessionPrincipal principal,
      @PathVariable String id,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    requireFields(body, Set.of("reason"));
    String reason = text(body, "reason");
    AgentIfMatch precondition = ifMatch(request);
    return response(
        commands.release(
            parseId(id),
            principal.accountId(),
            precondition.disputeId(),
            precondition.version(),
            reason));
  }

  @PostMapping("/{id}/resolve")
  @AuthenticatedActivity
  public ResponseEntity<ApiEnvelope<?>> resolve(
      @AuthenticationPrincipal SessionPrincipal principal,
      @PathVariable String id,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    requireFields(body, Set.of("decision", "internalResolutionNotes", "customerResolutionMessage"));
    String decision = text(body, "decision");
    String internal = text(body, "internalResolutionNotes");
    String customer = text(body, "customerResolutionMessage");
    AgentIfMatch precondition = ifMatch(request);
    return response(
        commands.resolve(
            parseId(id),
            principal.accountId(),
            precondition.disputeId(),
            precondition.version(),
            decision,
            internal,
            customer));
  }

  private ResponseEntity<ApiEnvelope<?>> response(AgentDisputeDetailDto detail) {
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

  private static void requireFields(JsonNode body, Set<String> expected) {
    if (body == null || !body.isObject())
      throw new InvalidRequestException("A JSON object is required.");
    Set<String> actual = new HashSet<>();
    body.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected))
      throw new InvalidRequestException("Request fields do not match the command contract.");
  }

  private static String text(JsonNode body, String field) {
    JsonNode value = body.get(field);
    if (value == null || !value.isTextual())
      throw new InvalidRequestException("Required text field is missing.");
    return value.textValue();
  }
}
