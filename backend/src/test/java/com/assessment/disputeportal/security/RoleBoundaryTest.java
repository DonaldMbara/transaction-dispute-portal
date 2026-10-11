package com.assessment.disputeportal.security;

import com.assessment.disputeportal.security.session.AccountSessionValidationFilter;

import com.assessment.disputeportal.common.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.GenericFilterBean;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

@Import(RoleBoundaryTest.Fixture.class)
class RoleBoundaryTest extends AuthenticationApiIntegrationTest {
    @Test void realAuthorizationBoundaryRejectsOppositeRoleAndAnonymousBeforeTerminalFilter() throws Exception {
        for(String role:new String[]{"CUSTOMER","AGENT"}) {
            reset();account(role,true);var b=request("GET","/api/v1/auth/csrf",Map.of());var result=login(cookie(b),body(b).at("/data/csrfToken").asText());
            var headers=Map.of("Cookie",cookie(result),"X-Role","ROLE_"+(role.equals("CUSTOMER")?"AGENT":"CUSTOMER"));
            for(String required:new String[]{"CUSTOMER","AGENT"}) {
                var response=request("GET","/api/v1/role-fixture/"+required.toLowerCase(),headers);
                assertThat(response.statusCode()).isEqualTo(role.equals(required)?200:403);
                assertThat(response.body().contains("terminal")).isEqualTo(role.equals(required));
            }
        }
        var safe=request("GET","/api/v1/role-fixture/customer",Map.of());assertThat(safe.statusCode()).isEqualTo(401);
        var missing=request("POST","/api/v1/role-fixture/customer",Map.of());assertThat(missing.statusCode()).isEqualTo(403);assertThat(body(missing).path("error_code").asInt()).isEqualTo(1010);
        var b=request("GET","/api/v1/auth/csrf",Map.of());assertThat(post("/api/v1/role-fixture/customer",cookie(b),body(b).at("/data/csrfToken").asText(),"").statusCode()).isEqualTo(401);
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class Fixture {
        @Bean @Order(0) SecurityFilterChain boundary(HttpSecurity http,AccountSessionValidationFilter validation,
                HttpSessionSecurityContextRepository contexts,HttpSessionCsrfTokenRepository csrf,ApiErrorWriter errors) throws Exception {
            return http.securityMatcher("/api/v1/role-fixture/**")
                .securityContext(c->c.requireExplicitSave(true).securityContextRepository(contexts))
                .addFilterBefore(validation,AnonymousAuthenticationFilter.class)
                .csrf(c->c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(c->c.requestMatchers("/api/v1/role-fixture/customer").hasRole("CUSTOMER")
                    .requestMatchers("/api/v1/role-fixture/agent").hasRole("AGENT").anyRequest().denyAll())
                .exceptionHandling(c->c.authenticationEntryPoint((req,res,ex)->AccountSessionValidationFilter.unauthorized(res,errors))
                    .accessDeniedHandler((req,res,ex)->errors.write(res,403,ApiEnvelope.failure(ex instanceof CsrfException?1010:1002,"forbidden","Access denied","Access denied"))))
                .addFilterAfter(new GenericFilterBean() {
                    public void doFilter(jakarta.servlet.ServletRequest req,jakarta.servlet.ServletResponse res,jakarta.servlet.FilterChain chain) throws java.io.IOException {
                        errors.write((jakarta.servlet.http.HttpServletResponse)res,200,ApiEnvelope.success("terminal"));
                    }
                },AuthorizationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable).build();
        }
    }
}
