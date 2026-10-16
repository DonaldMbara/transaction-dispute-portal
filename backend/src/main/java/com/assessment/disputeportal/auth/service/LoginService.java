package com.assessment.disputeportal.auth.service;

import com.assessment.disputeportal.auth.model.AccountIdentity;
import com.assessment.disputeportal.auth.repository.AccountLookup;
import com.assessment.disputeportal.auth.validation.EmailNormalizer;
import com.assessment.disputeportal.auth.validation.PasswordPolicy;
import com.assessment.disputeportal.common.exception.RateLimitedException;
import com.assessment.disputeportal.security.session.SessionActivityStore;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
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
  private final LoginAttemptLimiter loginAttempts;
  private final LoginClientAddressResolver clientAddressResolver;
  private final MeterRegistry meters;

  public LoginService(
      AuthenticationManager authManager,
      HttpSessionCsrfTokenRepository csrfRepository,
      HttpSessionSecurityContextRepository contextRepository,
      SessionActivityStore sessionActivity,
      EmailNormalizer emailNormalizer,
      AccountLookup accounts,
      LoginAttemptLimiter loginAttempts,
      LoginClientAddressResolver clientAddressResolver,
      MeterRegistry meters) {
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
    this.loginAttempts = loginAttempts;
    this.clientAddressResolver = clientAddressResolver;
    this.meters = meters;
  }

  public AccountIdentity login(
      String rawEmail,
      String rawPassword,
      String correlationId,
      HttpServletRequest request,
      HttpServletResponse response) {

    String email = emailNormalizer.normalize(rawEmail);
    String password = PasswordPolicy.validateForVerification(rawPassword);
    String clientAddress = clientAddressResolver.resolve(request);
    loginAttempts
        .retryAfter(clientAddress, email)
        .ifPresent(retryAfter -> throwRateLimited(retryAfter, correlationId));

    var credentials = UsernamePasswordAuthenticationToken.unauthenticated(email, password);
    credentials.setDetails(correlationId);

    org.springframework.security.core.Authentication verified;
    try {
      verified = authManager.authenticate(credentials);
    } catch (BadCredentialsException failure) {
      loginAttempts
          .recordFailure(clientAddress, email)
          .ifPresent(retryAfter -> throwRateLimited(retryAfter, correlationId));
      throw failure;
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
      loginAttempts.recordSuccess(clientAddress, email);

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

  private void throwRateLimited(Duration retryAfter, String correlationId) {
    long seconds = Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    meters.counter("portal.auth.login.rate_limited").increment();
    AUDIT.info(
        "timestamp={} operation=login outcome=rate-limited retryAfter={} correlation={}",
        Instant.now(),
        seconds,
        correlationId);
    throw new RateLimitedException(Duration.ofSeconds(seconds));
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
