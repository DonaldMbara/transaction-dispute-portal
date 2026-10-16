package com.assessment.disputeportal.auth.cli;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.assessment.disputeportal.auth.model.AccountIdentity;
import com.assessment.disputeportal.auth.model.AccountRole;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountAdminCommandTest {
  private static final String PASSWORD = "synthetic console secret";

  static class ConsoleFixture implements AccountAdminCommand.MaskedConsole {
    char[] first = PASSWORD.toCharArray();
    char[] second = PASSWORD.toCharArray();
    int prompts;
    String output = "";

    public char[] readPassword(String prompt) {
      assertThat(prompt).doesNotContain(PASSWORD);
      return prompts++ == 0 ? first : second;
    }

    public void message(String message) {
      output += message;
    }
  }

  @Test
  void createRequiresExplicitSupportedRoleAndStatusAndForwardsExactPassword() {
    var console = new ConsoleFixture();
    var service = mock(AccountAdministrationService.class);
    UUID id = UUID.randomUUID();
    when(service.create(eq("fixture@example.test"), eq("CUSTOMER"), eq(false), eq(PASSWORD), any()))
        .thenReturn(
            new AccountIdentity(id, "fixture@example.test", AccountRole.CUSTOMER, false, 0));
    assertThat(
            AccountAdminCommand.run(
                new String[] {
                  "create",
                  "--email",
                  "fixture@example.test",
                  "--role",
                  "CUSTOMER",
                  "--enabled",
                  "false"
                },
                Map.of("DB_PASSWORD", "database-only"),
                console,
                service))
        .isZero();
    assertThat(console.prompts).isEqualTo(2);
    assertThat(console.output).contains(id.toString(), "auth_version=0").doesNotContain(PASSWORD);
    assertThat(console.first).containsOnly('\0');
    assertThat(console.second).containsOnly('\0');
  }

  @Test
  void credentialsAndMissingConsoleAreRejectedBeforeAnyServiceCall() {
    var console = new ConsoleFixture();
    var service = mock(AccountAdministrationService.class);
    String[] arguments = {
      "disable", "--id", UUID.randomUUID().toString(), "--expected-version", "0"
    };
    assertThatThrownBy(() -> AccountAdminCommand.validate(arguments, Map.of(), null))
        .isInstanceOf(InvalidRequestException.class);
    for (String key :
        new String[] {
          "ACCOUNT_PASSWORD",
          "NEW_PASSWORD",
          "PASSWORD",
          "INITIAL_PASSWORD",
          "SPRING_DATASOURCE_PASSWORD"
        }) {
      assertThat(AccountAdminCommand.run(arguments, Map.of(key, PASSWORD), console, service))
          .isEqualTo(1);
    }
    assertThat(
            AccountAdminCommand.run(
                new String[] {"create", "--password", PASSWORD}, Map.of(), console, service))
        .isEqualTo(1);
    verifyNoInteractions(service);
    assertThat(console.prompts).isZero();
    assertThat(console.output).doesNotContain(PASSWORD);
  }

  @Test
  void invalidMissingDuplicateAndUnsupportedOperandsAreRejected() {
    var service = mock(AccountAdministrationService.class);
    var console = new ConsoleFixture();
    for (String[] arguments :
        new String[][] {
          {},
          {"delete"},
          {"create", "--email", "x", "--role", "ADMIN", "--enabled", "true"},
          {"create", "--email", "x", "--role", "AGENT", "--enabled", "yes"},
          {"create", "--email", "x", "--role", "AGENT"},
          {"create", "--email", "x", "--email", "y", "--enabled", "true"},
          {"disable", "--id", "invalid", "--expected-version", "0"},
          {"enable", "--id", UUID.randomUUID().toString(), "--expected-version", "-1"},
          {"enable", "--id", UUID.randomUUID().toString(), "--expected-version", "oops"}
        }) {
      assertThat(AccountAdminCommand.run(arguments, Map.of(), console, service)).isEqualTo(1);
    }
    verifyNoInteractions(service);
    assertThat(console.prompts).isZero();
  }

  @Test
  void mismatchCancellationAndServiceFailureDoNotExposeSecretsAndClearBuffers() {
    var service = mock(AccountAdministrationService.class);
    String[] args = {
      "replace-password", "--id", UUID.randomUUID().toString(), "--expected-version", "0"
    };
    var mismatch = new ConsoleFixture();
    mismatch.second = "different secret".toCharArray();
    assertThat(AccountAdminCommand.run(args, Map.of(), mismatch, service)).isEqualTo(1);
    verifyNoInteractions(service);
    assertThat(mismatch.first).containsOnly('\0');
    assertThat(mismatch.second).containsOnly('\0');
    var cancelled = new ConsoleFixture();
    cancelled.first = null;
    assertThat(AccountAdminCommand.run(args, Map.of(), cancelled, service)).isEqualTo(1);
    verifyNoInteractions(service);
    var failure = new ConsoleFixture();
    when(service.replacePassword(any(), anyLong(), any(), any()))
        .thenThrow(new InvalidRequestException(PASSWORD));
    assertThat(AccountAdminCommand.run(args, Map.of(), failure, service)).isEqualTo(1);
    assertThat(failure.output).doesNotContain(PASSWORD);
    assertThat(failure.first).containsOnly('\0');
  }

  @Test
  void maintenanceUsesExplicitVersionAndPasswordPromptsOnlyForReplacement() {
    var service = mock(AccountAdministrationService.class);
    UUID id = UUID.randomUUID();
    var identity = new AccountIdentity(id, "fixture@example.test", AccountRole.AGENT, true, 4);
    when(service.setEnabled(eq(id), eq(3L), anyBoolean(), any())).thenReturn(identity);
    when(service.setRole(eq(id), eq(3L), eq("AGENT"), any())).thenReturn(identity);
    when(service.replacePassword(eq(id), eq(3L), eq(PASSWORD), any())).thenReturn(identity);
    for (String operation : new String[] {"disable", "enable", "set-role", "replace-password"}) {
      var console = new ConsoleFixture();
      String[] args =
          operation.equals("set-role")
              ? new String[] {
                operation, "--id", id.toString(), "--expected-version", "3", "--role", "AGENT"
              }
              : new String[] {operation, "--id", id.toString(), "--expected-version", "3"};
      assertThat(AccountAdminCommand.run(args, Map.of(), console, service)).isZero();
      assertThat(console.prompts).isEqualTo(operation.equals("replace-password") ? 2 : 0);
    }
  }
}
