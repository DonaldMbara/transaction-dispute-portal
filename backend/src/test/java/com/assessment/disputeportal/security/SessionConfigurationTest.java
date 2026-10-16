package com.assessment.disputeportal.security;

import com.assessment.disputeportal.security.config.SessionConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import com.assessment.disputeportal.common.web.ApiErrorWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

class SessionConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(SessionConfiguration.class)
            .withBean(ApiErrorWriter.class, () -> new ApiErrorWriter(new ObjectMapper()));
    @Test void defaultAndProductionCookiesAreFixedSecureHostOnlyAndNonpersistent() {
        for(String profile : new String[]{"test","production"}) runner.withPropertyValues("spring.profiles.active="+profile).run(context -> {
            assertThat(context).hasNotFailed();
            var request=new MockHttpServletRequest();
            request.addHeader("X-Forwarded-Proto","http");
            var response=new MockHttpServletResponse();
            context.getBean(CookieSerializer.class).writeCookieValue(new CookieSerializer.CookieValue(request,response,"opaque-test-id"));
            String cookie=response.getHeader("Set-Cookie");
            assertThat(cookie).contains("SESSION=","Path=/api/v1","HttpOnly","Secure","SameSite=Lax")
                    .doesNotContain("Domain=","Max-Age=","Expires=");
        });
    }
    @Test void explicitLocalAllowsHttpAndProductionConflictsFailClosed() {
        runner.withPropertyValues("spring.profiles.active=local","portal.session.cookie-secure=false").run(context -> {
            assertThat(context).hasNotFailed();
            var response=new MockHttpServletResponse();
            context.getBean(CookieSerializer.class).writeCookieValue(new CookieSerializer.CookieValue(new MockHttpServletRequest(),response,"opaque-test-id"));
            assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly","SameSite=Lax").doesNotContain("Secure");
        });
        for(String[] properties : new String[][]{
                {"spring.profiles.active=production,local"}, {"portal.session.cookie-secure=false"},
                {"spring.profiles.active=production","portal.session.cookie-secure=false"},
                {"spring.profiles.active=production","server.servlet.session.cookie.secure=false"}}) {
            runner.withPropertyValues(properties).run(context -> assertThat(context).hasFailed());
        }
    }
    @Test void outerAvailabilityFilterPrecedesSpringSessionAndServletTrackingIsCookieOnly() {
        runner.run(context -> {
            var filter=context.getBean("availabilityFilter",FilterRegistrationBean.class);
            assertThat(filter.getOrder()).isLessThan(SessionRepositoryFilter.DEFAULT_ORDER);
            var servlet=new org.springframework.mock.web.MockServletContext();
            assertThatCode(() -> context.getBean("cookieOnlyTracking", org.springframework.boot.web.servlet.ServletContextInitializer.class).onStartup(servlet)).doesNotThrowAnyException();
            assertThat(servlet.getEffectiveSessionTrackingModes()).containsExactly(jakarta.servlet.SessionTrackingMode.COOKIE);
        });
    }
}
