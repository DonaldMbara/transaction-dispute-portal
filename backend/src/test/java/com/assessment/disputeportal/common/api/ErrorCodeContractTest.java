package com.assessment.disputeportal.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeContractTest {
  @Test
  void errorCodesStatusesAndTypesMatchThePublishedApiContract() {
    var actual =
        Arrays.stream(ErrorCode.values())
            .collect(
                Collectors.toMap(
                    ErrorCode::name,
                    code -> new ContractEntry(code.code(), code.status(), code.type())));

    assertThat(actual)
        .containsExactlyInAnyOrderEntriesOf(
            Map.ofEntries(
                Map.entry(
                    "AUTHENTICATION_REQUIRED",
                    new ContractEntry(1001, HttpStatus.UNAUTHORIZED, "unauthenticated")),
                Map.entry("FORBIDDEN", new ContractEntry(1002, HttpStatus.FORBIDDEN, "forbidden")),
                Map.entry(
                    "RESOURCE_NOT_FOUND",
                    new ContractEntry(1003, HttpStatus.NOT_FOUND, "resource-not-found")),
                Map.entry(
                    "INVALID_REQUEST",
                    new ContractEntry(1004, HttpStatus.BAD_REQUEST, "validation-failed")),
                Map.entry(
                    "RATE_LIMITED",
                    new ContractEntry(1005, HttpStatus.TOO_MANY_REQUESTS, "rate-limited")),
                Map.entry(
                    "REQUEST_TOO_LARGE",
                    new ContractEntry(1006, HttpStatus.PAYLOAD_TOO_LARGE, "request-too-large")),
                Map.entry(
                    "UNSUPPORTED_MEDIA_TYPE",
                    new ContractEntry(
                        1007, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type")),
                Map.entry(
                    "METHOD_NOT_ALLOWED",
                    new ContractEntry(1008, HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed")),
                Map.entry(
                    "NOT_ACCEPTABLE",
                    new ContractEntry(1009, HttpStatus.NOT_ACCEPTABLE, "not-acceptable")),
                Map.entry(
                    "CSRF_REJECTED",
                    new ContractEntry(1010, HttpStatus.FORBIDDEN, "csrf-rejected")),
                Map.entry(
                    "TRANSACTION_NOT_ELIGIBLE",
                    new ContractEntry(
                        2002, HttpStatus.UNPROCESSABLE_ENTITY, "transaction-not-eligible")),
                Map.entry(
                    "DISPUTE_ALREADY_EXISTS",
                    new ContractEntry(2003, HttpStatus.CONFLICT, "dispute-already-exists")),
                Map.entry(
                    "IDEMPOTENCY_KEY_REUSED",
                    new ContractEntry(2004, HttpStatus.CONFLICT, "idempotency-key-reused")),
                Map.entry(
                    "DISPUTE_STATE_CONFLICT",
                    new ContractEntry(2005, HttpStatus.CONFLICT, "dispute-state-conflict")),
                Map.entry(
                    "VERSION_STALE",
                    new ContractEntry(2006, HttpStatus.PRECONDITION_FAILED, "version-stale")),
                Map.entry(
                    "PRECONDITION_REQUIRED",
                    new ContractEntry(
                        2007, HttpStatus.PRECONDITION_REQUIRED, "precondition-required")),
                Map.entry(
                    "ACCOUNT_STATE_CONFLICT",
                    new ContractEntry(2008, HttpStatus.CONFLICT, "account-state-conflict")),
                Map.entry(
                    "INTERNAL_ERROR",
                    new ContractEntry(3001, HttpStatus.INTERNAL_SERVER_ERROR, "internal-error")),
                Map.entry(
                    "STORE_UNAVAILABLE",
                    new ContractEntry(
                        3002, HttpStatus.SERVICE_UNAVAILABLE, "service-unavailable"))));
    assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::code)).doesNotHaveDuplicates();
  }

  @Test
  void eachErrorCodeHasAProblemSlugAndHttpStatus() {
    assertThat(ErrorCode.values())
        .allSatisfy(
            code -> {
              assertThat(code.type()).matches("[a-z][a-z0-9-]*");
              assertThat(code.status().isError()).isTrue();
            });
  }

  private record ContractEntry(int code, HttpStatus status, String type) {}
}
