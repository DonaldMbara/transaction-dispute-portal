package com.assessment.disputeportal.transaction.controller;

import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.common.api.PageResponse;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import com.assessment.disputeportal.security.web.AuthenticatedActivity;
import com.assessment.disputeportal.transaction.mapper.TransactionResponseMapper;
import com.assessment.disputeportal.transaction.model.dto.TransactionResponse;
import com.assessment.disputeportal.transaction.service.TransactionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/transactions")
public final class TransactionController {
  private final TransactionQueryParser parser;
  private final TransactionQueryService transactions;
  private final TransactionResponseMapper mapper;

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
            name = "search",
            in = ParameterIn.QUERY,
            description = "Literal merchant or reference search, up to 100 characters.",
            schema = @Schema(maxLength = 100)),
        @Parameter(
            name = "accountId",
            in = ParameterIn.QUERY,
            description = "Optional owned account selector.",
            schema = @Schema(format = "uuid")),
        @Parameter(
            name = "direction",
            in = ParameterIn.QUERY,
            schema =
                @Schema(
                    allowableValues = {"ALL", "IN", "OUT"},
                    defaultValue = "ALL")),
        @Parameter(
            name = "status",
            in = ParameterIn.QUERY,
            schema =
                @Schema(
                    allowableValues = {"ALL", "PENDING", "COMPLETED", "FAILED"},
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
            name = "minAmount",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string", pattern = "^[0-9]{1,17}(\\.[0-9]{1,2})?$")),
        @Parameter(
            name = "maxAmount",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string", pattern = "^[0-9]{1,17}(\\.[0-9]{1,2})?$")),
        @Parameter(
            name = "sortBy",
            in = ParameterIn.QUERY,
            schema =
                @Schema(
                    allowableValues = {"occurredAt", "amount", "merchant", "reference"},
                    defaultValue = "occurredAt")),
        @Parameter(
            name = "sortDirection",
            in = ParameterIn.QUERY,
            schema =
                @Schema(
                    allowableValues = {"ASC", "DESC"},
                    defaultValue = "DESC"))
      })
  public ResponseEntity<ApiEnvelope<PageResponse<TransactionResponse>>> list(
      @AuthenticationPrincipal SessionPrincipal principal, HttpServletRequest request) {
    var query = parser.parse(queryParameters(request));
    var page = transactions.list(principal.accountId(), query);
    var response =
        new PageResponse<>(
            page.total(),
            page.page(),
            page.size(),
            page.items().stream().map(mapper::toResponse).toList());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiEnvelope.success(response));
  }

  @GetMapping("/{id}")
  @AuthenticatedActivity
  public ResponseEntity<ApiEnvelope<TransactionResponse>> get(
      @AuthenticationPrincipal SessionPrincipal principal,
      @PathVariable String id,
      HttpServletRequest request) {
    if (!request.getParameterMap().isEmpty())
      throw new InvalidRequestException("Transaction detail does not accept query parameters.");
    var transaction = transactions.get(principal.accountId(), parser.parsePathId(id));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiEnvelope.success(mapper.toResponse(transaction)));
  }

  private static Map<String, List<String>> queryParameters(HttpServletRequest request) {
    return request.getParameterMap().entrySet().stream()
        .collect(
            Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(Arrays.asList(entry.getValue()))));
  }
}
