package com.assessment.disputeportal.security;

import com.assessment.disputeportal.auth.model.AccountIdentity;
import com.assessment.disputeportal.auth.model.Account;
import com.assessment.disputeportal.auth.model.AccountRole;
import com.assessment.disputeportal.auth.model.AppUser;
import com.assessment.disputeportal.common.exception.DomainInvariantException;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.security.authentication.VersionedPasswordEncoder;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordEncodingTest {
    private final VersionedPasswordEncoder encoder = new VersionedPasswordEncoder();
    private static final String PREFIX = "{" + VersionedPasswordEncoder.ID + "}";

    @Test
    void randomSaltsAndIndependentJceDerivationConfirmTheVersionedParameters() throws Exception {
        String raw = "isolated encoder fixture";
        String first = encoder.encode(raw);
        String second = encoder.encode(raw);
        byte[] payload = HexFormat.of().parseHex(first.substring(PREFIX.length()));
        assertThat(first.startsWith(PREFIX)).isTrue();
        assertThat(first.length()).isEqualTo(PREFIX.length() + 96).isLessThanOrEqualTo(255);
        assertThat(payload.length).isEqualTo(16 + 32);
        assertThat(Arrays.equals(Arrays.copyOf(payload, 16),
                Arrays.copyOf(HexFormat.of().parseHex(second.substring(PREFIX.length())), 16))).isFalse();
        var spec = new PBEKeySpec(raw.toCharArray(), Arrays.copyOf(payload, 16), 600_000, 256);
        try {
            byte[] expected = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            assertThat(Arrays.equals(expected, Arrays.copyOfRange(payload, 16, 48))).isTrue();
        } finally { spec.clearPassword(); }
        assertThat(encoder.matches(raw, first)).isTrue();
        assertThat(encoder.matches(raw, second)).isTrue();
        assertThat(encoder.matches("different fixture password", first)).isFalse();
    }

    @Test
    void acceptedRangeSpacesAndUnicodeAreNotTruncatedTrimmedOrNormalized() {
        for (String raw : new String[]{"a".repeat(8), "😀".repeat(128), " ".repeat(8), "  exact fixture password  ", "e\u0301".repeat(16)}) {
            String hash = encoder.encode(raw);
            assertThat(encoder.matches(raw, hash)).isTrue();
            if (!raw.equals(raw.trim())) assertThat(encoder.matches(raw.trim(), hash)).isFalse();
            if (raw.contains("\u0301")) assertThat(encoder.matches("é".repeat(16), hash)).isFalse();
        }
        String first = "😀".repeat(24) + "a";
        assertThat(encoder.matches("😀".repeat(24) + "b", encoder.encode(first))).isFalse();
    }

    @Test
    void invalidInputsAndUnknownOrCorruptFormatsFailClosed() {
        for (String raw : new String[]{null, "a".repeat(7), "a".repeat(129), "abcdefg\uD800"}) {
            assertThatThrownBy(() -> encoder.encode(raw)).isInstanceOf(InvalidRequestException.class);
            assertThat(encoder.matches(raw, "{pbkdf2-sha256-v1}" + "0".repeat(96))).isFalse();
        }
        for (String hash : new String[]{null, "plaintext", "{noop}plaintext", "{bcrypt}unsupported", "{pbkdf2-sha256-v2}" + "0".repeat(96),
                PREFIX + "0".repeat(95), PREFIX + "g".repeat(96)}) {
            assertThat(encoder.matches("isolated fixture password", hash)).isFalse();
        }
    }

    @Test
    void verifiesACompatibleExistingHashWithoutApplyingTheCreationMinimum() throws Exception {
        String raw = "short";
        byte[] salt = HexFormat.of().parseHex("00112233445566778899aabbccddeeff");
        var spec = new PBEKeySpec(raw.toCharArray(), salt, 600_000, 256);
        byte[] derived;
        try {
            derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally { spec.clearPassword(); }
        String existing = PREFIX + HexFormat.of().formatHex(salt) + HexFormat.of().formatHex(derived);
        assertThat(encoder.matches(raw, existing)).isTrue();
        assertThat(encoder.matches("other", existing)).isFalse();
        assertThatThrownBy(() -> encoder.encode(raw)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void entityAndLookupProjectionDoNotExposeCredentialHashes() throws Exception {
        String hash = encoder.encode("isolated fixture password");
        var account = new AppUser();
        account.setId(UUID.randomUUID());
        account.setEmail("privacy@example.test");
        account.setPasswordHash(hash);
        account.setRole(AccountRole.CUSTOMER);
        account.setEnabled(true);
        account.setCreatedAt(Instant.now());
        var mapper = new ObjectMapper().findAndRegisterModules().setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        assertThat(mapper.writeValueAsString(account).contains(hash)).isFalse();
        assertThat(mapper.writeValueAsString(account).contains("passwordHash")).isFalse();
        assertThat(account.toString().contains(hash)).isFalse();
        var identity = new AccountIdentity(account.getId(), account.getEmail(), account.getRole(), account.isEnabled(), account.getAuthVersion());
        assertThat(mapper.writeValueAsString(identity).contains(hash)).isFalse();
        assertThat(identity.toString().contains(hash)).isFalse();
        assertThatThrownBy(() -> new Account(UUID.randomUUID(), "privacy@example.test", "plaintext",
            AccountRole.AGENT, true, Instant.now(), 0)).isInstanceOf(DomainInvariantException.class)
                .hasMessage("Account password hash has an unsupported format.");
    }

    @Test
    void benchmarkActualEncoderWithoutPrintingCredentials() {
        String raw = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) assertThat(encoder.matches(raw, encoder.encode(raw))).isTrue();
        double[] encode = new double[5];
        double[] verify = new double[5];
        for (int i = 0; i < 5; i++) {
            long start = System.nanoTime();
            String hash = encoder.encode(raw);
            encode[i] = (System.nanoTime() - start) / 1_000_000.0;
            start = System.nanoTime();
            boolean valid = encoder.matches(raw, hash);
            verify[i] = (System.nanoTime() - start) / 1_000_000.0;
            assertThat(valid).isTrue();
        }
        System.out.printf(Locale.ROOT, "PASSWORD_BENCHMARK java=%s vm=%s os=%s arch=%s processors=%d warmups=2 samples=5 iterations=600000 saltBytes=16 keyBits=256%n",
                System.getProperty("java.version"), System.getProperty("java.vm.name"), System.getProperty("os.name"),
                System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors());
        report("encode", encode);
        report("verify", verify);
    }

    private static void report(String operation, double[] samples) {
        double[] sorted = samples.clone();
        Arrays.sort(sorted);
        System.out.printf(Locale.ROOT, "PASSWORD_BENCHMARK %s_ms min=%.3f median=%.3f max=%.3f samples=%s%n", operation,
                sorted[0], sorted[2], sorted[4], Arrays.toString(samples));
    }
}
