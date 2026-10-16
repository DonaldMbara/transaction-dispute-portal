package com.assessment.disputeportal.common.web;

import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.common.api.ErrorCode;
import com.assessment.disputeportal.common.exception.BusinessConflictException;
import com.assessment.disputeportal.common.exception.DisputeApiException;
import com.assessment.disputeportal.common.exception.DomainInvariantException;
import com.assessment.disputeportal.common.exception.InvalidOperationException;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.common.exception.InvalidSessionAccessTimeException;
import com.assessment.disputeportal.common.exception.RateLimitedException;
import com.assessment.disputeportal.common.exception.ResourceNotFoundException;
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
  ResponseEntity<ApiEnvelope<?>> unauthenticated(jakarta.servlet.http.HttpServletRequest request) {
    var code = ErrorCode.AUTHENTICATION_REQUIRED;
    return ResponseEntity.status(code.status())
        .body(
            ApiEnvelope.failure(
                code.code(),
                code.type(),
                "Authentication required",
                request.getServletPath().equals("/api/v1/auth/login")
                    ? "Unable to sign in with these credentials."
                    : "Authentication is required."));
  }

  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.bind.MethodArgumentNotValidException.class
  })
  ResponseEntity<ApiEnvelope<?>> malformed() {
    var code = ErrorCode.INVALID_REQUEST;
    return ResponseEntity.status(code.status())
        .body(
            ApiEnvelope.failure(
                code.code(), code.type(), "Invalid request", "The request is invalid."));
  }

  @ExceptionHandler(InvalidRequestException.class)
  ResponseEntity<ApiEnvelope<?>> invalidRequest(InvalidRequestException failure) {
    var code = failure.errorCode();
    return ResponseEntity.status(code.status())
        .body(
            ApiEnvelope.failure(
                code.code(), code.type(), "Invalid request", "The request is invalid."));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ApiEnvelope<?>> notFound(ResourceNotFoundException failure) {
    var code = failure.errorCode();
    return ResponseEntity.status(code.status())
        .body(
            ApiEnvelope.failure(
                code.code(), code.type(), "Not found", "The requested resource was not found."));
  }

  @ExceptionHandler(BusinessConflictException.class)
  ResponseEntity<ApiEnvelope<?>> conflict(BusinessConflictException failure) {
    var code = failure.errorCode();
    return ResponseEntity.status(code.status())
        .body(
            ApiEnvelope.failure(
                code.code(),
                code.type(),
                "Account state conflict",
                "The requested account change conflicts with its current state."));
  }

  @ExceptionHandler(DisputeApiException.class)
  ResponseEntity<ApiEnvelope<?>> disputeFailure(DisputeApiException failure) {
    var code = failure.errorCode();
    var response =
        ResponseEntity.status(code.status())
            .cacheControl(org.springframework.http.CacheControl.noStore());
    if (failure.retryAfter()) response.header("Retry-After", "1");
    return response.body(
        ApiEnvelope.failure(code.code(), code.type(), failure.title(), failure.detail()));
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiEnvelope<?>> unsupportedMedia() {
    var code = ErrorCode.UNSUPPORTED_MEDIA_TYPE;
    return ResponseEntity.status(code.status())
        .body(
            ApiEnvelope.failure(
                code.code(),
                code.type(),
                "Unsupported media type",
                "Use application/json for this request."));
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
  ResponseEntity<ApiEnvelope<?>> unacceptableMedia() {
    var code = ErrorCode.NOT_ACCEPTABLE;
    return ResponseEntity.status(code.status())
        .body(
            ApiEnvelope.failure(
                code.code(), code.type(), "Not acceptable", "Request a supported JSON response."));
  }

  @ExceptionHandler(DomainInvariantException.class)
  ResponseEntity<ApiEnvelope<?>> domainInvariant(
      DomainInvariantException failure, jakarta.servlet.http.HttpServletResponse response) {
    return internalError(response, failure.errorCode());
  }

  @ExceptionHandler(InvalidOperationException.class)
  ResponseEntity<ApiEnvelope<?>> invalidOperation(InvalidOperationException failure) {
    var code = failure.errorCode();
    return ResponseEntity.status(code.status())
        .body(
            ApiEnvelope.failure(
                code.code(),
                code.type(),
                "Case cannot be changed",
                "This case is no longer available for that action."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiEnvelope<?>> unexpected(jakarta.servlet.http.HttpServletResponse response) {
    return internalError(response);
  }

  private ResponseEntity<ApiEnvelope<?>> internalError(
      jakarta.servlet.http.HttpServletResponse response) {
    return internalError(response, ErrorCode.INTERNAL_ERROR);
  }

  private ResponseEntity<ApiEnvelope<?>> internalError(
      jakarta.servlet.http.HttpServletResponse response, ErrorCode code) {
    log.warn("Unexpected request failure correlation={}", response.getHeader("X-Correlation-ID"));
    return ResponseEntity.status(code.status())
        .body(
            ApiEnvelope.failure(
                code.code(),
                code.type(),
                "Internal error",
                "The request could not be completed. Please try again."));
  }

  @ExceptionHandler({DataAccessException.class, TransactionException.class})
  ResponseEntity<ApiEnvelope<?>> unavailable() {
    var code = ErrorCode.STORE_UNAVAILABLE;
    return ResponseEntity.status(code.status())
        .header("Cache-Control", "no-store")
        .body(
            ApiEnvelope.failure(
                code.code(),
                code.type(),
                "Service temporarily unavailable",
                "The service is temporarily unavailable. Please try again."));
  }

  @ExceptionHandler(RateLimitedException.class)
  ResponseEntity<ApiEnvelope<?>> rateLimited(RateLimitedException failure) {
    var code = failure.errorCode();
    long retryAfter = Math.max(1, (failure.retryAfter().toMillis() + 999) / 1000);
    return ResponseEntity.status(code.status())
        .header("Retry-After", Long.toString(retryAfter))
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(
            ApiEnvelope.failure(
                code.code(),
                code.type(),
                "Too many attempts",
                "Unable to sign in with these credentials. Try again later."));
  }

  @ExceptionHandler(InvalidSessionAccessTimeException.class)
  ResponseEntity<ApiEnvelope<?>> invalidSessionAccessTime(
      InvalidSessionAccessTimeException failure) {
    var code = failure.errorCode();
    return ResponseEntity.status(code.status())
        .body(
            ApiEnvelope.failure(
                code.code(),
                code.type(),
                "Internal error",
                "The request could not be completed. Please try again."));
  }
}
