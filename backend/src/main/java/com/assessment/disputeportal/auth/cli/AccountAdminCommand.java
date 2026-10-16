package com.assessment.disputeportal.auth.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

import com.assessment.disputeportal.auth.cli.AccountAdministrationService;
import com.assessment.disputeportal.auth.model.AccountIdentity;
import com.assessment.disputeportal.auth.model.AccountRole;
import com.assessment.disputeportal.common.exception.InvalidRequestException;

import java.io.Console;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Only non-secret operands are accepted. Database credentials still use the existing external DB configuration. */
public final class AccountAdminCommand {
    private AccountAdminCommand() {}

    interface MaskedConsole {
        char[] readPassword(String prompt);
        void message(String message);
    }

    record Options(String operation, String email, UUID id, String role, boolean enabled, long version) {}

    public static int launch(String[] args) {
        Console systemConsole = System.console();
        MaskedConsole console = systemConsole == null ? null : new MaskedConsole() {
            public char[] readPassword(String prompt) { return systemConsole.readPassword("%s", prompt); }
            public void message(String message) { systemConsole.printf("%s%n", message); }
        };
        try {
            validate(args, System.getenv(), console);
            var application = new SpringApplication(AccountAdminConfiguration.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setAdditionalProfiles("account-admin");
            // Hibernate constraint diagnostics can contain input values. Operator results and audit are generic.
            application.setDefaultProperties(Map.of("logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper", "OFF"));
            // Fixed highest-priority startup property prevents inherited web-mode configuration from opening a listener.
            try (var context = application.run("--spring.main.web-application-type=none")) {
                return run(args, System.getenv(), console, context.getBean(AccountAdministrationService.class));
            }
        } catch (RuntimeException failure) {
            AccountAdministrationService.recordOutcome("invocation", null, System.getProperty("user.name"), UUID.randomUUID(), "failed");
            System.err.println("Account administration unavailable; verify configuration and inputs.");
            return 2;
        }
    }

    static Options validate(String[] args, Map<String, String> environment, MaskedConsole console) {
        if (console == null) throw new InvalidRequestException("An interactive masked console is required.");
        if (environment.keySet().stream().anyMatch(key -> {
            String upper = key.toUpperCase(java.util.Locale.ROOT);
            return upper.contains("PASSWORD") && !upper.equals("DB_PASSWORD");
        })) throw new InvalidRequestException("Account password environment input is forbidden.");
        if (args.length == 0) throw new InvalidRequestException("Operation required.");
        String operation = args[0];
        Set<String> keys = switch (operation) {
            case "create" -> Set.of("--email", "--role", "--enabled");
            case "disable", "enable", "replace-password" -> Set.of("--id", "--expected-version");
            case "set-role" -> Set.of("--id", "--expected-version", "--role");
            default -> throw new InvalidRequestException("Unsupported operation.");
        };
        var values = new java.util.HashMap<String, String>();
        if (args.length != 1 + keys.size() * 2) throw new InvalidRequestException("Invalid operands.");
        for (int index = 1; index < args.length; index += 2) {
            if (!keys.contains(args[index]) || values.putIfAbsent(args[index], args[index + 1]) != null) {
                throw new InvalidRequestException("Invalid operands.");
            }
        }
        String role = values.get("--role");
        if (role != null) AccountRole.parse(role);
        if (operation.equals("create")) {
            String status = values.get("--enabled");
            if (!"true".equals(status) && !"false".equals(status)) throw new InvalidRequestException("Explicit status required.");
            return new Options(operation, values.get("--email"), null, role, Boolean.parseBoolean(status), 0);
        }
        long version;
        try {
            version = Long.parseLong(values.get("--expected-version"));
        } catch (NumberFormatException invalidVersion) {
            throw new InvalidRequestException("Invalid account version.", invalidVersion);
        }
        if (version < 0) throw new InvalidRequestException("Invalid account version.");
        String identifier = values.get("--id");
        UUID id;
        try {
            id = UUID.fromString(identifier);
        } catch (IllegalArgumentException invalidIdentifier) {
            throw new InvalidRequestException("Invalid account identifier.", invalidIdentifier);
        }
        if (!id.toString().equalsIgnoreCase(identifier)) throw new InvalidRequestException("Invalid account identifier.");
        return new Options(operation, null, id, role, false, version);
    }

    static int run(String[] args, Map<String, String> environment, MaskedConsole console, AccountAdministrationService service) {
        char[] first = null;
        char[] confirmation = null;
        Options options = null;
        boolean serviceInvoked = false;
        try {
            options = validate(args, environment, console);
            if (options.operation().equals("create") || options.operation().equals("replace-password")) {
                first = console.readPassword("Password (masked): ");
                confirmation = console.readPassword("Confirm password (masked): ");
                if (first == null || confirmation == null || !Arrays.equals(first, confirmation)) {
                    throw new InvalidRequestException("Password confirmation failed.");
                }
            }
            String operator = System.getProperty("user.name"); // Trust derives from controlled host access, not a portal role.
            String password = first == null ? null : new String(first);
            serviceInvoked = true;
            AccountIdentity result = switch (options.operation()) {
                case "create" -> service.create(options.email(), options.role(), options.enabled(), password, operator);
                case "disable", "enable" -> service.setEnabled(options.id(), options.version(), options.operation().equals("enable"), operator);
                case "set-role" -> service.setRole(options.id(), options.version(), options.role(), operator);
                case "replace-password" -> service.replacePassword(options.id(), options.version(), password, operator);
                default -> throw new InvalidRequestException("Unsupported operation.");
            };
            console.message("Committed account=" + result.id() + " auth_version=" + result.authVersion());
            return 0;
        } catch (RuntimeException failure) {
            if (!serviceInvoked) {
                AccountAdministrationService.recordOutcome(options == null ? "invocation" : options.operation(),
                        options == null ? null : options.id(), System.getProperty("user.name"), UUID.randomUUID(), "failed");
            }
            if (console != null) console.message("Account change rejected; inspect account state before retrying.");
            return 1;
        } finally {
            if (first != null) Arrays.fill(first, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
}
