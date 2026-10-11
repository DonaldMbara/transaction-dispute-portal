package com.assessment.disputeportal;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import com.assessment.disputeportal.auth.cli.AccountAdminCommand;
import com.assessment.disputeportal.account.cli.AccountFixtureCommand;
import com.assessment.disputeportal.transaction.cli.TransactionFixtureCommand;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DisputePortalApplication {
    public static void main(String[] args) {
        if (args.length > 0 && "account-fixture".equals(args[0])) {
            System.exit(AccountFixtureCommand.launch(Arrays.copyOfRange(args, 1, args.length)));
            return;
        }
        if (args.length > 0 && "transaction-fixture".equals(args[0])) {
            System.exit(TransactionFixtureCommand.launch(Arrays.copyOfRange(args, 1, args.length)));
            return;
        }
        if (args.length > 0 && ("account-admin".equals(args[0]) || "provision".equals(args[0]))) {
            System.exit(AccountAdminCommand.launch(Arrays.copyOfRange(args, 1, args.length)));
            return;
        }
        SpringApplication.run(DisputePortalApplication.class, args);
    }
}
