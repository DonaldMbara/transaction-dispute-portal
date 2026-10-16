package com.assessment.disputeportal.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.assessment.disputeportal.auth.model.AccountRole;
import com.assessment.disputeportal.security.session.AccountSessionValidator;
import com.assessment.disputeportal.security.session.SessionActivitySnapshot;
import com.assessment.disputeportal.security.session.SessionActivityStore;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class AccountSessionValidationTest {
  final UUID account = UUID.randomUUID();
  final SessionActivityStore store = mock(SessionActivityStore.class);
  final AccountSessionValidator validator = new AccountSessionValidator(store);

  SessionActivitySnapshot snapshot(
      long last, long now, String role, boolean enabled, long captured, long current) {
    return new SessionActivitySnapshot(account, captured, current, enabled, role, last, now);
  }

  @Test
  void exactIdleBoundaryExpiresAndJustBeforeBoundaryIsValidForBothRoles() {
    for (String role : new String[] {"CUSTOMER", "AGENT"}) {
      when(store.inspect("fixture-session"))
          .thenReturn(Optional.of(snapshot(200_000, 2_000_000, role, true, 2, 2)));
      assertThat(validator.validate(new SessionPrincipal(account), "fixture-session", 1_999_000))
          .isEmpty();
      when(store.inspect("fixture-session"))
          .thenReturn(Optional.of(snapshot(200_001, 2_000_000, role, true, 2, 2)));
      var validated =
          validator
              .validate(new SessionPrincipal(account), "fixture-session", 1_999_000)
              .orElseThrow();
      assertThat(validated.role()).isEqualTo(AccountRole.parse(role));
      assertThat(validated.accessTime()).isEqualTo(1_999_000);
      assertThat(validated.version()).isEqualTo(2);
    }
  }

  @Test
  void missingMismatchedExpiredRevokedDisabledAndUnsupportedAccountsFailClosed() {
    for (var invalid :
        java.util.List.of(
            snapshot(199_999, 2_000_000, "CUSTOMER", true, 2, 2),
            snapshot(200_001, 2_000_000, "CUSTOMER", false, 2, 2),
            snapshot(200_001, 2_000_000, "CUSTOMER", true, 1, 2),
            snapshot(200_001, 2_000_000, "ADMIN", true, 2, 2),
            snapshot(200_001, 2_000_000, "CUSTOMER", true, -1, -1))) {
      when(store.inspect("fixture-session")).thenReturn(Optional.of(invalid));
      assertThat(validator.validate(new SessionPrincipal(account), "fixture-session", 1_999_000))
          .isEmpty();
    }
    when(store.inspect("fixture-session")).thenReturn(Optional.empty());
    assertThat(validator.validate(new SessionPrincipal(account), "fixture-session", 1_999_000))
        .isEmpty();
    when(store.inspect("fixture-session"))
        .thenReturn(Optional.of(snapshot(200_001, 2_000_000, "CUSTOMER", true, 2, 2)));
    assertThat(
            validator.validate(
                new SessionPrincipal(UUID.randomUUID()), "fixture-session", 1_999_000))
        .isEmpty();
  }

  @Test
  void malformedPrincipalAndMissingFrameworkSessionNeverTriggerAccountLookup() {
    assertThat(validator.validate("forged principal", "fixture-session", 1)).isEmpty();
    assertThat(validator.validate(null, "fixture-session", 1)).isEmpty();
    assertThat(validator.validate(new SessionPrincipal(account), null, 1)).isEmpty();
    assertThat(validator.validate(new SessionPrincipal(account), "fixture-session", -1)).isEmpty();
    verifyNoInteractions(store);
  }

  @Test
  void storeFailuresPropagateInsteadOfBecomingInvalidCredentials() {
    when(store.inspect(anyString()))
        .thenThrow(new DataAccessResourceFailureException("isolated fixture"));
    assertThatThrownBy(
            () -> validator.validate(new SessionPrincipal(account), "fixture-session", 1))
        .isInstanceOf(DataAccessResourceFailureException.class);
  }

  @Test
  void credentialFreePrincipalRoundTripsThroughItsVersionedSerializedForm() throws Exception {
    var principal = new SessionPrincipal(account);
    var bytes = new java.io.ByteArrayOutputStream();
    try (var output = new java.io.ObjectOutputStream(bytes)) {
      output.writeObject(principal);
    }
    try (var input =
        new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
      assertThat(input.readObject()).isEqualTo(principal);
    }
    assertThat(java.io.ObjectStreamClass.lookup(SessionPrincipal.class).getSerialVersionUID())
        .isEqualTo(1);
    assertThat(SessionPrincipal.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .containsExactly("accountId");
  }
}
