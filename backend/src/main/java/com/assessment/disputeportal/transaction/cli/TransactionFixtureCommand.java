package com.assessment.disputeportal.transaction.cli;

import java.util.Arrays;

import com.assessment.disputeportal.transaction.service.TransactionFixtureService;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class TransactionFixtureCommand {
    private TransactionFixtureCommand() {}

    public static int launch(String[] arguments) {
        String email = parseEmail(arguments);
        if (email == null) {
            System.err.println("Usage: transaction-fixture --email <existing-customer-email>");
            return 2;
        }
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TransactionFixtureConfiguration.class)
                .web(WebApplicationType.NONE).profiles("transaction-fixture").run()) {
            int total = context.getBean(TransactionFixtureService.class).ensureCustomerFixtures(email);
            System.out.printf("Transaction fixtures verified: rows=%d%n", total);
            return 0;
        } catch (Exception failure) {
            System.err.println("Transaction fixture command could not complete safely.");
            return 1;
        }
    }

    private static String parseEmail(String[] arguments) {
        if (arguments == null || arguments.length != 2 || !"--email".equals(arguments[0])
                || arguments[1] == null || arguments[1].isBlank()) return null;
        return arguments[1];
    }
}
