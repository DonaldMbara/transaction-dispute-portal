package com.assessment.disputeportal.security.config;

import com.assessment.disputeportal.common.web.ApiErrorWriter;
import com.assessment.disputeportal.security.session.AccountSessionValidationFilter;
import com.assessment.disputeportal.security.session.AccountSessionValidator;
import com.assessment.disputeportal.security.web.AuthenticationActivityInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class AuthenticationActivityConfiguration implements WebMvcConfigurer {
  private final AuthenticationActivityInterceptor activity;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(activity);
  }

  @Bean
  AccountSessionValidationFilter accountSessionValidationFilter(
      AccountSessionValidator validator, ApiErrorWriter errors) {
    return new AccountSessionValidationFilter(validator, errors);
  }

  @Bean
  FilterRegistrationBean<AccountSessionValidationFilter> accountValidationRegistration(
      AccountSessionValidationFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(
        false); // Security-chain filter only; no duplicate/global servlet invocation.
    return registration;
  }
}
