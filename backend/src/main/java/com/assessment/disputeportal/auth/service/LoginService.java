package com.assessment.disputeportal.auth.service;

import com.assessment.disputeportal.auth.model.AccountIdentity;
import com.assessment.disputeportal.auth.repository.AccountLookup;
import com.assessment.disputeportal.auth.validation.EmailNormalizer;
import com.assessment.disputeportal.auth.validation.PasswordPolicy;
import com.assessment.disputeportal.security.session.SessionActivityStore;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.stereotype.Service;

/** Coordinates credential verification and establishment of an authenticated session. */
@Service
public final class LoginService {

  private static final org.slf4j.Logger AUDIT = LoggerFactory.getLogger("authentication.audit");

  private final AuthenticationManager authManager;
  private final CompositeSessionAuthenticationStrategy sessionStrategy;
  private final HttpSessionSecurityContextRepository contextRepository;
  private final SessionActivityStore sessionActivity;
  private final EmailNormalizer emailNormalizer;
  private final AccountLookup accounts;

  public LoginService(
      AuthenticationManager authManager,
      HttpSessionCsrfTokenRepository csrfRepository,
      HttpSessionSecurityContextRepository contextRepository,
      SessionActivityStore sessionActivity,
      EmailNormalizer emailNormalizer,
      AccountLookup accounts) {
    this.authManager = authManager;
    this.sessionStrategy =
        new CompositeSessionAuthenticationStrategy(
            List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfRepository)));
    this.contextRepository = contextRepository;
    this.sessionActivity = sessionActivity;
    this.emailNormalizer = emailNormalizer;
    this.accounts = accounts;
  }

  public AccountIdentity login(
      String rawEmail,
      String rawPassword,
      String correlationId,
      HttpServletRequest request,
      HttpServletResponse response) {

    String email = emailNormalizer.normalize(rawEmail);
    String password = PasswordPolicy.validateForVerification(rawPassword);

    var credentials = UsernamePasswordAuthenticationToken.unauthenticated(email, password);
    credentials.setDetails(correlationId);

    org.springframework.security.core.Authentication verified;
    try {
      verified = authManager.authenticate(credentials);
    } finally {
      credentials.eraseCredentials();
    }

    var account = (AccountIdentity) verified.getPrincipal();
    var authenticated =
        UsernamePasswordAuthenticationToken.authenticated(
            new SessionPrincipal(account.id()), null, verified.getAuthorities());

    try {
      sessionStrategy.onAuthentication(authenticated, request, response);
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authenticated);
      SecurityContextHolder.setContext(context);
      contextRepository.saveContext(context, request, response);

      var session = request.getSession(false);
      if (!sessionActivity.activate(session.getId(), account, session.getLastAccessedTime())) {
        throw new BadCredentialsException("Unable to sign in with these credentials.");
      }

      AUDIT.info(
          "timestamp={} operation=login outcome=success account={} correlation={}",
          Instant.now(),
          account.id(),
          correlationId);

      return account;

    } catch (RuntimeException failure) {
      AUDIT.info(
          "timestamp={} operation=login outcome=activation-failed account={} correlation={}",
          Instant.now(),
          account.id(),
          correlationId);
      SecurityContextHolder.clearContext();
      try {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
      } catch (RuntimeException ignored) {
      }
      throw failure;
    }
  }

  public AccountIdentity currentIdentity(SessionPrincipal principal) {
    return accounts
        .findById(principal.accountId())
        .orElseThrow(
            () ->
                new org.springframework.security.authentication.BadCredentialsException(
                    "Authentication is required."));
  }
}
