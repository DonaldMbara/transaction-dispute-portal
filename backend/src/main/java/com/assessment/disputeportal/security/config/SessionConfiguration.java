package com.assessment.disputeportal.security.config;

import com.assessment.disputeportal.common.exception.InvalidOperationException;
import com.assessment.disputeportal.common.web.ApiErrorWriter;
import com.assessment.disputeportal.security.session.SessionTimeoutProperties;
import com.assessment.disputeportal.security.web.AvailabilityFilter;
import jakarta.servlet.SessionTrackingMode;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SessionTimeoutProperties.class)
public class SessionConfiguration {
  @Bean
  CookieSerializer cookieSerializer(Environment environment) {
    boolean local = environment.acceptsProfiles(Profiles.of("local"));
    boolean production = environment.acceptsProfiles(Profiles.of("production"));
    if (production && local)
      throw new InvalidOperationException("Production and local profiles cannot be combined.");
    boolean secure = environment.getProperty("portal.session.cookie-secure", Boolean.class, true);
    Boolean servletOverride =
        environment.getProperty("server.servlet.session.cookie.secure", Boolean.class);
    if ((!secure || Boolean.FALSE.equals(servletOverride)) && !local) {
      throw new InvalidOperationException(
          "Insecure session cookies require the explicit local profile.");
    }
    var cookies = new DefaultCookieSerializer();
    cookies.setCookieName("SESSION");
    cookies.setCookiePath("/api/v1");
    cookies.setUseHttpOnlyCookie(true);
    cookies.setSameSite("Lax");
    cookies.setUseSecureCookie(secure);
    cookies.setCookieMaxAge(-1);
    return cookies;
  }

  // DispatcherServlet's default flash-map lookup calls getSession(false), even for REST health
  // GETs.
  // Use the public MVC extension point to skip only those exact operational reads.
  @Bean
  org.springframework.web.servlet.FlashMapManager flashMapManager() {
    var delegate = new org.springframework.web.servlet.support.SessionFlashMapManager();
    return new org.springframework.web.servlet.FlashMapManager() {
      public org.springframework.web.servlet.FlashMap retrieveAndUpdate(
          jakarta.servlet.http.HttpServletRequest request,
          jakarta.servlet.http.HttpServletResponse response) {
        return SecurityConfiguration.isHealthRequest(request)
            ? null
            : delegate.retrieveAndUpdate(request, response);
      }

      public void saveOutputFlashMap(
          org.springframework.web.servlet.FlashMap flashMap,
          jakarta.servlet.http.HttpServletRequest request,
          jakarta.servlet.http.HttpServletResponse response) {
        delegate.saveOutputFlashMap(flashMap, request, response);
      }
    };
  }

  @Bean
  ServletContextInitializer cookieOnlyTracking() {
    return servlet -> servlet.setSessionTrackingModes(Set.of(SessionTrackingMode.COOKIE));
  }

  @Bean
  FilterRegistrationBean<AvailabilityFilter> availabilityFilter(ApiErrorWriter errors) {
    var registration = new FilterRegistrationBean<>(new AvailabilityFilter(errors));
    registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER - 1);
    registration.addUrlPatterns("/*");
    return registration;
  }
}
