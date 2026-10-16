package com.assessment.disputeportal.transaction.cli;

import com.assessment.disputeportal.account.cli.AccountFixtureRepository;
import com.assessment.disputeportal.account.cli.AccountFixtureService;
import com.assessment.disputeportal.auth.repository.JpaAccountLookup;
import com.assessment.disputeportal.auth.validation.EmailNormalizer;
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
@EntityScan(
    basePackages = {
      "com.assessment.disputeportal.auth.model.entity",
      "com.assessment.disputeportal.account.model.entity",
      "com.assessment.disputeportal.transaction.model.entity"
    })
@EnableJpaRepositories(
    basePackages = {
      "com.assessment.disputeportal.auth.repository",
      "com.assessment.disputeportal.account.repository"
    })
@Import({
  EmailNormalizer.class,
  JpaAccountLookup.class,
  AccountFixtureRepository.class,
  AccountFixtureService.class,
  TransactionFixtureRepository.class,
  TransactionFixtureService.class
})
public class TransactionFixtureConfiguration {}
