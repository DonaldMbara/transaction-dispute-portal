package com.assessment.disputeportal.auth.service;

import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.stereotype.Service;

import com.assessment.disputeportal.security.session.SessionPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Ends the current session and clears its security and CSRF state. */
@Service
@RequiredArgsConstructor
public final class LogoutService {

    private static final org.slf4j.Logger AUDIT = LoggerFactory.getLogger("authentication.audit");

    private final CookieSerializer cookieSerializer;
    private final HttpSessionCsrfTokenRepository csrfRepository;

    public void logout(HttpServletRequest request, HttpServletResponse response, String correlationId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID accountId = authentication != null && authentication.getPrincipal() instanceof SessionPrincipal principal
                ? principal.accountId() : null;

        var session = request.getSession(false);
        if (session != null) session.invalidate();

        SecurityContextHolder.clearContext();
        csrfRepository.saveToken(null, request, response);
        cookieSerializer.writeCookieValue(new CookieSerializer.CookieValue(request, response, ""));

        AUDIT.info("timestamp={} operation=logout outcome=success account={} correlation={}",
                Instant.now(), accountId, correlationId);
    }
}