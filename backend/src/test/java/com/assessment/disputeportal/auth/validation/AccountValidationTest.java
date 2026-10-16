package com.assessment.disputeportal.auth.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assessment.disputeportal.auth.model.AccountRole;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AccountValidationTest {
  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private final EmailNormalizer emails = new EmailNormalizer(FACTORY.getValidator());

  @AfterAll
  static void closeValidator() {
    FACTORY.close();
  }

  @Test
  void normalizesAsciiEdgeWhitespaceAndCaseWithoutProviderSpecificFolding() {
    assertThat(emails.normalize(" \t\n\u000b\f\rCustomer.Name+tag@EXAMPLE.TEST \r\n\t"))
        .isEqualTo("customer.name+tag@example.test");
    assertThat(emails.normalize("customer.name+tag@example.test"))
        .isEqualTo("customer.name+tag@example.test");
  }

  @Test
  void normalizationDoesNotDependOnTheDefaultLocale() {
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertThat(emails.normalize("IDENTITY@EXAMPLE.TEST")).isEqualTo("identity@example.test");
    } finally {
      Locale.setDefault(original);
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        " ",
        "not-email",
        "customer name@example.test",
        "\u2003customer@example.test\u2003"
      })
  void rejectsInvalidEmailsWithoutEchoingInput(String input) {
    assertThatThrownBy(() -> emails.normalize(input))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid login email.");
  }

  @Test
  void enforces254CharacterEmailBound() {
    String email =
        "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
    assertThat(emails.normalize(email)).hasSize(254);
    assertThatThrownBy(() -> emails.normalize(email + "d"))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void rolesAreExactlyTheTwoSupportedServerRoles() {
    assertThat(AccountRole.parse("CUSTOMER")).isEqualTo(AccountRole.CUSTOMER);
    assertThat(AccountRole.parse("AGENT")).isEqualTo(AccountRole.AGENT);
    for (String role : new String[] {null, "", "ADMIN", "customer", " AGENT", "ROLE_AGENT"}) {
      assertThatThrownBy(() -> AccountRole.parse(role))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessage("Unsupported account role.");
    }
  }

  @Test
  void creationCountsCodePointsAndPreservesSpacesAndCombiningSequences() {
    for (String password :
        new String[] {
          "a".repeat(8),
          "a".repeat(128),
          "😀".repeat(8),
          "😀".repeat(128),
          " ".repeat(8),
          "  exact password  ",
          "e\u0301".repeat(8)
        }) {
      assertThat(PasswordPolicy.validateForCreation(password)).isSameAs(password);
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"short", "\uD800abcdefg", "abcdefg\uDC00", "abcdefg\uD800x"})
  void creationRejectsMalformedUnicodeAndShortPasswords(String password) {
    assertThatThrownBy(() -> PasswordPolicy.validateForCreation(password))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Password must contain 8–128 well-formed Unicode code points.");
  }

  @Test
  void creationRejects7And129CodePointsIncludingMultibyteValues() {
    for (int length : new int[] {7, 129}) {
      assertThatThrownBy(() -> PasswordPolicy.validateForCreation("a".repeat(length)))
          .isInstanceOf(InvalidRequestException.class);
      assertThatThrownBy(() -> PasswordPolicy.validateForCreation("😀".repeat(length)))
          .isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  void verificationSeparatelyAcceptsBoundedExistingCredentials() {
    assertThat(PasswordPolicy.validateForVerification("a")).isEqualTo("a");
    assertThat(PasswordPolicy.validateForVerification("a".repeat(128))).hasSize(128);
    for (String password : new String[] {"", "a".repeat(129), "\uD800"}) {
      assertThatThrownBy(() -> PasswordPolicy.validateForVerification(password))
          .isInstanceOf(InvalidRequestException.class);
    }
  }
}
