package com.assessment.disputeportal.auth.cli;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;

import com.assessment.disputeportal.auth.service.AccountAdministrationService;
import com.assessment.disputeportal.auth.repository.AccountRepository;
import com.assessment.disputeportal.auth.repository.JpaAccountRepository;
import com.assessment.disputeportal.auth.mapper.AccountMapper;
import com.assessment.disputeportal.auth.validation.EmailNormalizer;
import com.assessment.disputeportal.auth.model.entity.AppUser;
import com.assessment.disputeportal.auth.repository.AppUserRepository;
import com.assessment.disputeportal.security.authentication.VersionedPasswordEncoder;

/** Loaded only by the explicit non-web entry point, not by normal application startup. */
@Configuration(proxyBeanMethods = false)
@Profile("account-admin")
@EnableAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
@EntityScan(basePackageClasses = AppUser.class)
@EnableJpaRepositories(basePackageClasses = AppUserRepository.class)
@Import({EmailNormalizer.class, AccountMapper.class, JpaAccountRepository.class,
        VersionedPasswordEncoder.class})
class AccountAdminConfiguration {
    @Bean
    AccountAdministrationService accountAdministrationService(AccountRepository accounts, EmailNormalizer emails,
            VersionedPasswordEncoder passwords, PlatformTransactionManager transactions) {
        return new AccountAdministrationService(accounts, emails, passwords, transactions);
    }
}
