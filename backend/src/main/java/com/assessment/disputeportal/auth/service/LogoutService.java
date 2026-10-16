package com.assessment.disputeportal.auth.service;

import com.assessment.disputeportal.security.session.SessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.stereotype.Service;

/** Ends the current session and clears its security and CSRF state. */
@Service
@RequiredArgsConstructor
@Slf4j(topic = "authentication.audit")
public final class LogoutService {

  private final CookieSerializer cookieSerializer;
  private final HttpSessionCsrfTokenRepository csrfRepository;

  public void logout(
      HttpServletRequest request, HttpServletResponse response, String correlationId) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    UUID accountId =
        authentication != null
                && authentication.getPrincipal() instanceof SessionPrincipal principal
            ? principal.accountId()
            : null;

    var session = request.getSession(false);
    if (session != null) session.invalidate();

    SecurityContextHolder.clearContext();
    csrfRepository.saveToken(null, request, response);
    cookieSerializer.writeCookieValue(new CookieSerializer.CookieValue(request, response, ""));

    log.info(
        "timestamp={} operation=logout outcome=success account={} correlation={}",
        Instant.now(),
        accountId,
        correlationId);
  }
}
