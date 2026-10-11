package com.assessment.disputeportal.transaction.cli;

import com.assessment.disputeportal.account.repository.AccountFixtureRepository;
import com.assessment.disputeportal.account.service.AccountFixtureService;
import com.assessment.disputeportal.auth.repository.JpaAccountLookup;
import com.assessment.disputeportal.auth.validation.EmailNormalizer;
import com.assessment.disputeportal.transaction.repository.TransactionFixtureRepository;
import com.assessment.disputeportal.transaction.service.TransactionFixtureService;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@Profile("transaction-fixture")
@EnableAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
@EntityScan(basePackages = {"com.assessment.disputeportal.auth.model", "com.assessment.disputeportal.account.repository",
        "com.assessment.disputeportal.transaction.repository"})
@EnableJpaRepositories(basePackages = {"com.assessment.disputeportal.auth.repository", "com.assessment.disputeportal.account.repository"})
@Import({EmailNormalizer.class, JpaAccountLookup.class, AccountFixtureRepository.class, AccountFixtureService.class,
        TransactionFixtureRepository.class, TransactionFixtureService.class})
public class TransactionFixtureConfiguration {}
