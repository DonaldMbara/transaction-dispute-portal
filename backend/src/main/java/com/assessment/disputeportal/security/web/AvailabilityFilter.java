package com.assessment.disputeportal.security.web;

import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.common.web.ApiErrorWriter;
import com.assessment.disputeportal.common.web.StoreFailureClassifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/** Outer servlet filter: handles only uncommitted failures, without custom response buffering. */
@Slf4j
@RequiredArgsConstructor
public final class AvailabilityFilter extends OncePerRequestFilter {
  private final ApiErrorWriter errors;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String incoming = request.getHeader("X-Correlation-ID");
    String correlation =
        incoming != null && incoming.matches("[A-Za-z0-9_-]{1,64}")
            ? incoming
            : UUID.randomUUID().toString();
    response.setHeader("X-Correlation-ID", correlation);
    response.setHeader("Cache-Control", "no-store");
    MDC.put("correlationId", correlation);
    try {
      chain.doFilter(request, response);
    } catch (ServletException | RuntimeException failure) {
      if (!StoreFailureClassifier.isStoreFailure(failure)) throw failure;
      log.warn(
          "Store operation failed correlation={} committed={}",
          correlation,
          response.isCommitted());
      if (response.isCommitted())
        throw new ServletException("Store operation failed after response commitment.");
      response.reset(); // Includes any pending session-cookie header. Restore safe headers only.
      response.setHeader("X-Correlation-ID", correlation);
      errors.write(response, 503, ApiEnvelope.unavailable());
    } finally {
      MDC.remove("correlationId");
    }
  }
}
