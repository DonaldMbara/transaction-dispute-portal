package com.assessment.disputeportal.security.session;

import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.common.web.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public final class AccountSessionValidationFilter extends OncePerRequestFilter {
  private final AccountSessionValidator validator;
  private final ApiErrorWriter errors;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)) {
      var session = request.getSession(false);
      var validated =
          session == null || !authentication.isAuthenticated()
              ? java.util.Optional.<ValidatedSession>empty()
              : validator.validate(
                  authentication.getPrincipal(), session.getId(), session.getLastAccessedTime());
      if (validated.isEmpty()) {
        clear(request);
        // Public bootstrap downgrades expired/revoked context before materializing a fresh
        // anonymous token.
        if (!("GET".equals(request.getMethod())
                && "/api/v1/auth/csrf".equals(request.getServletPath()))
            && !("POST".equals(request.getMethod())
                && java.util.Set.of("/api/v1/auth/login", "/api/v1/auth/logout")
                    .contains(request.getServletPath()))) {
          unauthorized(response, errors);
          return;
        }
      } else {
        var state = validated.orElseThrow();
        request.setAttribute(ValidatedSession.ATTRIBUTE, state);
        var current = SecurityContextHolder.createEmptyContext();
        current.setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                authentication.getPrincipal(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + state.role().name()))));
        // Authorities on the deserialized object are not trusted or re-saved by this request.
        SecurityContextHolder.setContext(current);
      }
    }
    chain.doFilter(request, response);
  }

  public static void clear(HttpServletRequest request) {
    SecurityContextHolder.clearContext();
    request.removeAttribute(ValidatedSession.ATTRIBUTE);
    var session = request.getSession(false);
    if (session != null) session.invalidate();
  }

  public static void unauthorized(HttpServletResponse response, ApiErrorWriter errors)
      throws IOException {
    errors.write(
        response,
        401,
        ApiEnvelope.failure(
            1001, "unauthenticated", "Authentication required", "Authentication is required."));
  }
}
