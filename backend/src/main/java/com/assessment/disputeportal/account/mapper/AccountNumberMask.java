package com.assessment.disputeportal.account.mapper;

import java.util.regex.Pattern;

/** Account-number format and masking policy. This type exposes no persistence model or repository. */
public final class AccountNumberMask {
    public static final int ACCOUNT_NUMBER_LENGTH = 10;
    private static final Pattern WELL_FORMED = Pattern.compile("[0-9]{" + ACCOUNT_NUMBER_LENGTH + "}");
    private static final String MASK = "****";

    private AccountNumberMask() {}

    public static boolean isWellFormed(String value) {
        return value != null && WELL_FORMED.matcher(value).matches();
    }

    public static String mask(String value) {
        return isWellFormed(value) ? MASK + value.substring(ACCOUNT_NUMBER_LENGTH - 4) : MASK;
    }

    /** Returns a safe category for operational logs; never returns any part of the value. */
    public static String malformedReason(String value) {
        if (value == null) return "null";
        if (value.isEmpty()) return "empty";
        if (value.length() != ACCOUNT_NUMBER_LENGTH) return "length";
        return "non-ascii-digit";
    }

    public static boolean isMalformedPersistedValue(String value) {
        return !isWellFormed(value);
    }
}