package com.assessment.disputeportal;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.assessment.disputeportal.auth.cli.AccountAdminCommand;
import com.assessment.disputeportal.account.cli.AccountFixtureCommand;
import com.assessment.disputeportal.transaction.cli.TransactionFixtureCommand;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EntityScan(basePackages = {
    "com.assessment.disputeportal.auth.model.entity",
    "com.assessment.disputeportal.account.model.entity",
    "com.assessment.disputeportal.transaction.model.entity"
})
@EnableJpaRepositories(basePackages = {
    "com.assessment.disputeportal.auth.repository",
    "com.assessment.disputeportal.account.repository"
})
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
