package com.assessment.disputeportal.account.cli;

import com.assessment.disputeportal.account.model.AccountType;
import com.assessment.disputeportal.auth.model.entity.AppUser;
import com.assessment.disputeportal.auth.repository.AppUserRepository;
import com.assessment.disputeportal.auth.repository.JpaAccountLookup;
import com.assessment.disputeportal.auth.validation.EmailNormalizer;
import com.assessment.disputeportal.account.cli.AccountFixtureRepository;
import com.assessment.disputeportal.account.cli.AccountFixtureService;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Minimal non-web Spring context, loaded only by the explicit account-fixture command. */
@Configuration(proxyBeanMethods = false)
@Profile("account-fixture")
@EnableAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
@EntityScan(basePackages = {
        "com.assessment.disputeportal.auth.model.entity",
        "com.assessment.disputeportal.account.model.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.assessment.disputeportal.auth.repository",
        "com.assessment.disputeportal.account.repository"
})
@Import({EmailNormalizer.class, JpaAccountLookup.class, AccountFixtureRepository.class, AccountFixtureService.class})
class AccountFixtureConfiguration {}
