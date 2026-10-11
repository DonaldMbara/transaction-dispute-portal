package com.assessment.disputeportal.account.cli;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import com.assessment.disputeportal.account.service.AccountFixtureService;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Explicit operator entry point for deterministic, non-destructive account fixture data. */
public final class AccountFixtureCommand {
    private AccountFixtureCommand() {}

    public static int launch(String[] arguments) {
        String customerEmail = parseCustomerEmail(arguments);
        if (customerEmail == null) {
            System.err.println("Usage: account-fixture --email <existing-customer-email>");
            return 2;
        }
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(AccountFixtureConfiguration.class)
                .web(WebApplicationType.NONE).profiles("account-fixture").run()) {
            Map<String, UUID> accounts = context.getBean(AccountFixtureService.class)
                    .createFixtureAccounts(customerEmail);
            System.out.printf("Fixture accounts verified: MAIN=%s SAVINGS=%s%n",
                    accounts.get("MAIN"), accounts.get("SAVINGS"));
            return 0;
        } catch (Exception failure) {
            System.err.println("Account fixture command could not complete safely.");
            return 1;
        }
    }

    private static String parseCustomerEmail(String[] arguments) {
        if (arguments == null || arguments.length != 2 || !"--email".equals(arguments[0])
                || arguments[1] == null || arguments[1].isBlank()) return null;
        return arguments[1];
    }
}
