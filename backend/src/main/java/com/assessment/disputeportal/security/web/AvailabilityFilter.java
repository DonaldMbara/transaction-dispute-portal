package com.assessment.disputeportal.security.web;

import lombok.RequiredArgsConstructor;

import com.assessment.disputeportal.common.ApiEnvelope;
import com.assessment.disputeportal.common.ApiErrorWriter;
import com.assessment.disputeportal.common.StoreFailureClassifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

/** Outer servlet filter: handles only uncommitted failures, without custom response buffering. */
@RequiredArgsConstructor
public final class AvailabilityFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityFilter.class);
    private final ApiErrorWriter errors;

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader("X-Correlation-ID");
        String correlation = incoming != null && incoming.matches("[A-Za-z0-9_-]{1,64}") ? incoming : UUID.randomUUID().toString();
        response.setHeader("X-Correlation-ID", correlation);
        response.setHeader("Cache-Control", "no-store");
        try { chain.doFilter(request, response); }
        catch (ServletException | RuntimeException failure) {
            if (!StoreFailureClassifier.isStoreFailure(failure)) throw failure;
            LOG.warn("Store operation failed correlation={} committed={}", correlation, response.isCommitted());
            if (response.isCommitted()) throw new ServletException("Store operation failed after response commitment.");
            response.reset(); // Includes any pending session-cookie header. Restore safe headers only.
            response.setHeader("X-Correlation-ID", correlation);
            errors.write(response, 503, ApiEnvelope.unavailable());
        }
    }
}
