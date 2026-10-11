package com.assessment.disputeportal.auth.validation;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;
import com.assessment.disputeportal.common.exception.InvalidRequestException;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public final class EmailNormalizer {
    private final Validator validator;

    public String normalize(String email) {
        if (email == null) throw new InvalidRequestException("Invalid login email.");
        int start = 0;
        int end = email.length();
        while (start < end && isAsciiWhitespace(email.charAt(start))) start++;
        while (end > start && isAsciiWhitespace(email.charAt(end - 1))) end--;
        String canonical = email.substring(start, end).toLowerCase(Locale.ROOT);
        if (canonical.codePoints().anyMatch(point -> Character.isWhitespace(point)
                || Character.isSpaceChar(point) || Character.isISOControl(point))
                || !validator.validate(new LoginEmail(canonical)).isEmpty()) {
            throw new InvalidRequestException("Invalid login email.");
        }
        return canonical;
    }

    private static boolean isAsciiWhitespace(char value) {
        return value == ' ' || (value >= '\t' && value <= '\r');
    }

    private record LoginEmail(@NotBlank @Email @Size(max = 254) String value) {}
}
