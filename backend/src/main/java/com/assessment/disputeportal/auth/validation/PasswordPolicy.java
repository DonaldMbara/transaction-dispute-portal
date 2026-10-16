package com.assessment.disputeportal.auth.validation;

import com.assessment.disputeportal.common.exception.InvalidRequestException;

public final class PasswordPolicy {
  private static final int CREATION_MINIMUM = 8;
  private static final int VERIFICATION_MINIMUM = 1;
  private static final int MAXIMUM = 128;

  /**
   * The only supported encoded hash format: versioned prefix followed by 96 lowercase hex
   * characters.
   */
  private static final java.util.regex.Pattern SUPPORTED_HASH =
      java.util.regex.Pattern.compile("\\{pbkdf2-sha256-v1\\}[0-9a-f]{96}");

  private PasswordPolicy() {}

  /**
   * Returns true if the stored value is a recognised encoded hash that this application can verify.
   */
  public static boolean isSupportedHash(String value) {
    return value != null && SUPPORTED_HASH.matcher(value).matches();
  }

  /** Applies the approved policy for provisioning and password replacement. */
  public static String validateForCreation(CharSequence password) {
    return validate(
        password, CREATION_MINIMUM, "Password must contain 8–128 well-formed Unicode code points.");
  }

  /**
   * Applies only the safe login admission bound. It deliberately does not apply the creation
   * minimum so compatible existing hashes remain usable.
   */
  public static String validateForVerification(CharSequence password) {
    return validate(
        password,
        VERIFICATION_MINIMUM,
        "Password must contain 1–128 well-formed Unicode code points.");
  }

  private static String validate(CharSequence password, int minimum, String message) {
    if (password == null) throw invalid(message);
    String value = password.toString();
    int points = 0;
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      if (Character.isHighSurrogate(current)) {
        if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index)))
          throw invalid(message);
      } else if (Character.isLowSurrogate(current)) {
        throw invalid(message);
      }
      if (++points > MAXIMUM) throw invalid(message);
    }
    if (points < minimum) throw invalid(message);
    return value;
  }

  private static InvalidRequestException invalid(String message) {
    return new InvalidRequestException(message);
  }
}
