package com.assessment.disputeportal.security.authentication;

import java.util.Map;

import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Component;

import com.assessment.disputeportal.auth.validation.PasswordPolicy;
import com.assessment.disputeportal.common.exception.InvalidRequestException;

@Component
public final class VersionedPasswordEncoder implements PasswordEncoder {
    public static final String ID = "pbkdf2-sha256-v1";
    private final PasswordEncoder delegate;

    public VersionedPasswordEncoder() {
        var pbkdf2 = new Pbkdf2PasswordEncoder("", 16, 600_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        pbkdf2.setEncodeHashAsBase64(false);
        delegate = new DelegatingPasswordEncoder(ID, Map.of(ID, pbkdf2));
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(PasswordPolicy.validateForCreation(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (!isSupportedFormat(encodedPassword)) return false;
        final String validated;
        try {
            validated = PasswordPolicy.validateForVerification(rawPassword);
        } catch (InvalidRequestException invalidInput) {
            return false;
        }
        return delegate.matches(validated, encodedPassword);
    }

    public static boolean isSupportedFormat(String value) {
        return PasswordPolicy.isSupportedHash(value);
    }
}