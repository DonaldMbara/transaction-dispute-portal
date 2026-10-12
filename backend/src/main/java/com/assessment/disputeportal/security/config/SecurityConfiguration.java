package com.assessment.disputeportal.security.config;

import com.assessment.disputeportal.common.ApiEnvelope;
import com.assessment.disputeportal.common.ApiErrorWriter;
import com.assessment.disputeportal.common.StoreFailureClassifier;
import com.assessment.disputeportal.security.authentication.AccountAuthenticationProvider;
import com.assessment.disputeportal.security.session.AccountSessionValidationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
        @Bean
        AuthenticationManager authenticationManager(AccountAuthenticationProvider provider) {
                return new ProviderManager(provider);
        }

    public static boolean isHealthRequest(jakarta.servlet.http.HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && java.util.Set.of(
                "/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness").contains(request.getServletPath());
    }
    @Bean @Order(1)
    SecurityFilterChain healthSecurity(HttpSecurity http) throws Exception {
        RequestMatcher exactHealth = SecurityConfiguration::isHealthRequest;
        return http.securityMatcher(exactHealth)
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .sessionManagement(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable).csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable).rememberMe(AbstractHttpConfigurer::disable).build();
    }
    @Bean org.springframework.security.web.firewall.RequestRejectedHandler rejectedRequestHandler(ApiErrorWriter errors) {
        return (request, response, failure) -> errors.write(response, 400,
                ApiEnvelope.failure(1004, "validation-failed", "Invalid request", "The request is invalid."));
    }
    @Bean HttpSessionCsrfTokenRepository csrfTokenRepository() {
        var repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        return repository;
    }
    @Bean HttpSessionSecurityContextRepository securityContextRepository() {
        var repository = new HttpSessionSecurityContextRepository();
        repository.setDisableUrlRewriting(true);
        return repository;
    }
    @Bean @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorWriter errors,
            HttpSessionCsrfTokenRepository csrf, HttpSessionSecurityContextRepository contexts,
            AccountSessionValidationFilter validator) throws Exception {
        AccessDeniedHandler denied = (request, response, failure) -> {
            if (StoreFailureClassifier.isStoreFailure(failure)) errors.write(response, 503, ApiEnvelope.unavailable());
            else if (failure instanceof CsrfException) errors.write(response, 403,
                    ApiEnvelope.failure(1010, "csrf-rejected", "Security verification failed",
                            "Security verification failed. Refresh the security token and try again."));
            else errors.write(response, 403, ApiEnvelope.failure(1002, "forbidden", "Access denied",
                    "You do not have permission to perform this operation."));
        };
        return http.addFilterBefore(validator, org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").hasAnyRole("CUSTOMER", "AGENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/accounts").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/transactions", "/api/v1/transactions/{id}").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/disputes").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/disputes", "/api/v1/disputes/**").hasRole("CUSTOMER")
                        .anyRequest().denyAll())
                .exceptionHandling(failures -> failures.accessDeniedHandler(denied)
                        .authenticationEntryPoint((request, response, failure) -> errors.write(response, 401,
                                ApiEnvelope.failure(1001, "unauthenticated", "Authentication required", "Authentication is required."))))
                .securityContext(context -> context.requireExplicitSave(true).securityContextRepository(contexts))
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler()))
                .requestCache(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .rememberMe(AbstractHttpConfigurer::disable).build();
    }
}
