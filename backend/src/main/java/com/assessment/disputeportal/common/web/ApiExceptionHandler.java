package com.assessment.disputeportal.common.web;

import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.common.exception.BusinessConflictException;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.common.exception.InvalidSessionAccessTimeException;
import com.assessment.disputeportal.common.exception.RateLimitedException;
import com.assessment.disputeportal.common.exception.ResourceNotFoundException;
import com.assessment.disputeportal.dispute.exception.DisputeApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j(topic = "application.error")
public final class ApiExceptionHandler {
  @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
  ResponseEntity<ApiEnvelope> unauthenticated(jakarta.servlet.http.HttpServletRequest request) {
    return ResponseEntity.status(401)
        .body(
            ApiEnvelope.failure(
                1001,
                "unauthenticated",
                "Authentication required",
                request.getServletPath().equals("/api/v1/auth/login")
                    ? "Unable to sign in with these credentials."
                    : "Authentication is required."));
  }

  @ExceptionHandler({
    InvalidRequestException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class
  })
  ResponseEntity<ApiEnvelope> malformed() {
    return ResponseEntity.badRequest()
        .body(
            ApiEnvelope.failure(
                1004, "validation-failed", "Invalid request", "The request is invalid."));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ApiEnvelope> notFound() {
    return ResponseEntity.status(404)
        .body(
            ApiEnvelope.failure(
                1003, "resource-not-found", "Not found", "The requested resource was not found."));
  }

  @ExceptionHandler(BusinessConflictException.class)
  ResponseEntity<ApiEnvelope> conflict() {
    return ResponseEntity.status(409)
        .body(
            ApiEnvelope.failure(
                2008,
                "account-state-conflict",
                "Account state conflict",
                "The requested account change conflicts with its current state."));
  }

  @ExceptionHandler(DisputeApiException.class)
  ResponseEntity<ApiEnvelope> disputeFailure(DisputeApiException failure) {
    var response =
        ResponseEntity.status(failure.status())
            .cacheControl(org.springframework.http.CacheControl.noStore());
    if (failure.retryAfter()) response.header("Retry-After", "1");
    return response.body(
        ApiEnvelope.failure(
            failure.errorCode(), failure.type(), failure.title(), failure.detail()));
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiEnvelope> unsupportedMedia() {
    return ResponseEntity.status(415)
        .body(
            ApiEnvelope.failure(
                1007,
                "unsupported-media-type",
                "Unsupported media type",
                "Use application/json for this request."));
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
  ResponseEntity<ApiEnvelope> unacceptableMedia() {
    return ResponseEntity.status(406)
        .body(
            ApiEnvelope.failure(
                1009, "not-acceptable", "Not acceptable", "Request a supported JSON response."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiEnvelope> unexpected(jakarta.servlet.http.HttpServletResponse response) {
    log.warn("Unexpected request failure correlation={}", response.getHeader("X-Correlation-ID"));
    return ResponseEntity.status(500)
        .body(
            ApiEnvelope.failure(
                3001,
                "internal-error",
                "Internal error",
                "The request could not be completed. Please try again."));
  }

  @ExceptionHandler({DataAccessException.class, TransactionException.class})
  ResponseEntity<ApiEnvelope> unavailable() {
    return ResponseEntity.status(503)
        .header("Cache-Control", "no-store")
        .body(ApiEnvelope.unavailable());
  }

  @ExceptionHandler(RateLimitedException.class)
  ResponseEntity<ApiEnvelope> rateLimited(RateLimitedException failure) {
    long retryAfter = Math.max(1, (failure.retryAfter().toMillis() + 999) / 1000);
    return ResponseEntity.status(429)
        .header("Retry-After", Long.toString(retryAfter))
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(
            ApiEnvelope.failure(
                1005,
                "rate-limited",
                "Too many attempts",
                "Unable to sign in with these credentials. Try again later."));
  }

  @ExceptionHandler(InvalidSessionAccessTimeException.class)
  ResponseEntity<ApiEnvelope> invalidSessionAccessTime() {
    return ResponseEntity.status(500)
        .body(
            ApiEnvelope.failure(
                3001,
                "internal-error",
                "Internal error",
                "The request could not be completed. Please try again."));
  }
}
