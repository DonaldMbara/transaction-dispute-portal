package com.assessment.disputeportal.auth.service;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

import com.assessment.disputeportal.auth.service.AccountAdministrationService;
import com.assessment.disputeportal.auth.validation.EmailNormalizer;
import com.assessment.disputeportal.auth.repository.AccountRepository;
import com.assessment.disputeportal.security.authentication.VersionedPasswordEncoder;
@TestConfiguration(proxyBeanMethods=false)
public class AccountAdministrationTestConfiguration {
    @Bean public AccountAdministrationService accountAdministrationService(AccountRepository repository,EmailNormalizer emails,
            VersionedPasswordEncoder passwords,PlatformTransactionManager transactions) {
        return new AccountAdministrationService(repository,emails,passwords,transactions);
    }
}
