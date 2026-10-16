package com.assessment.disputeportal.security.authentication;

import com.assessment.disputeportal.auth.model.AccountIdentity;
import com.assessment.disputeportal.auth.model.AccountRole;
import com.assessment.disputeportal.auth.port.AuthenticationCredentialsLookup;
import com.assessment.disputeportal.auth.validation.PasswordPolicy;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Slf4j(topic = "authentication.audit")
public final class AccountAuthenticationProvider implements AuthenticationProvider {

  private final AuthenticationCredentialsLookup credentials;
  private final PasswordEncoder passwords;
  private final String dummy;

  public AccountAuthenticationProvider(
      AuthenticationCredentialsLookup credentials, PasswordEncoder passwords) {
    this.credentials = credentials;
    this.passwords = passwords;
    this.dummy = passwords.encode(java.util.UUID.randomUUID().toString());
  }

  @Override
  public Authentication authenticate(Authentication input) {
    var account = credentials.findByEmail(input.getName()).orElse(null);
    String hash =
        account != null
                && account.enabled()
                && PasswordPolicy.isSupportedHash(account.passwordHash())
            ? account.passwordHash()
            : dummy;
    boolean matches = passwords.matches((String) input.getCredentials(), hash);

    AccountRole role = null;
    if (account != null) {
      try {
        role = AccountRole.parse(account.role());
      } catch (InvalidRequestException ignored) {
      }
    }
    if (!matches || account == null || !account.enabled() || role == null) {
      log.info(
          "timestamp={} operation=login outcome=rejected account={} correlation={}",
          java.time.Instant.now(),
          account == null ? null : account.id(),
          input.getDetails() instanceof String reference && reference.matches("[A-Za-z0-9_-]{1,64}")
              ? reference
              : null);
      throw new BadCredentialsException("Unable to sign in with these credentials.");
    }

    var identity =
        new AccountIdentity(account.id(), account.email(), role, true, account.authVersion());
    return UsernamePasswordAuthenticationToken.authenticated(
        identity, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
  }

  @Override
  public boolean supports(Class<?> type) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type);
  }
}
