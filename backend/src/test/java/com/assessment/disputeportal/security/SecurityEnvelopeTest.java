package com.assessment.disputeportal.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.assessment.disputeportal.common.web.ApiErrorWriter;
import com.assessment.disputeportal.security.web.AvailabilityFilter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityEnvelopeTest extends SessionIntegrationSupport {
  @Test
  void errorsAndSuccessUseEnvelopeCorrelationNoStoreAndNoRedirects() throws Exception {
    for (String method : new String[] {"GET", "POST"}) {
      var result =
          request(
              method, "/api/v1/unrecognized", Map.of("X-Correlation-ID", "valid_Reference-123"));
      assertThat(result.statusCode()).isEqualTo(method.equals("GET") ? 401 : 403);
      var body = body(result);
      assertThat(body.path("status").asBoolean()).isFalse();
      assertThat(body.has("data")).isTrue();
      assertThat(body.path("data").isNull()).isTrue();
      assertThat(body.path("error_code").asInt()).isEqualTo(method.equals("GET") ? 1001 : 1010);
      assertThat(body.path("error").path("type").asText()).startsWith("/problems/");
      assertThat(result.headers().firstValue("X-Correlation-ID")).contains("valid_Reference-123");
      assertThat(result.headers().firstValue("Cache-Control")).contains("no-store");
      assertThat(result.headers().firstValue("Content-Type").orElseThrow())
          .startsWith("application/json");
      assertThat(result.headers().firstValue("Location")).isEmpty();
    }
    var bootstrap =
        request("GET", "/api/v1/auth/csrf", Map.of("X-Correlation-ID", "invalid reference!"));
    assertThat(body(bootstrap).path("status").asBoolean()).isTrue();
    assertThat(body(bootstrap).has("error")).isFalse();
    assertThat(bootstrap.headers().firstValue("X-Correlation-ID").orElseThrow())
        .matches("[0-9a-f-]{36}");
    assertThat(cookie(bootstrap)).startsWith("SESSION=");
    assertThat(bootstrap.headers().firstValue("set-cookie").orElseThrow())
        .contains("Secure", "HttpOnly", "SameSite=Lax", "Path=/api/v1");
  }

  @Test
  void unsupportedPersistedPrincipalCannotReachEndpoints() throws Exception {
    org.springframework.session.Session fixture = sessions.createSession();
    var context =
        new org.springframework.security.core.context.SecurityContextImpl(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "isolated-test-principal",
                null,
                java.util.List.of(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_AGENT"))));
    fixture.setAttribute("SPRING_SECURITY_CONTEXT", context);
    String cookie =
        "SESSION="
            + java.util.Base64.getEncoder()
                .encodeToString(fixture.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    try {
      var denied = request("GET", "/api/v1/unrecognized", Map.of("Cookie", cookie));
      assertThat(denied.statusCode()).isEqualTo(401);
      assertThat(body(denied).path("error_code").asInt()).isEqualTo(1001);
      assertThat(denied.body()).doesNotContain("isolated-test-principal", "ROLE_AGENT");
    } finally {
      sessions.deleteById(fixture.getId());
    }
  }

  @Test
  void sessionReadFailureBecomesGeneric503WithoutSessionCookie() throws Exception {
    var bootstrap = request("GET", "/api/v1/auth/csrf", Map.of());
    doThrow(new DataAccessResourceFailureException("private fixture detail"))
        .when(sessions)
        .findById(anyString());
    try {
      var failure = request("GET", "/api/v1/auth/csrf", Map.of("Cookie", cookie(bootstrap)));
      assertThat(failure.statusCode()).isEqualTo(503);
      assertThat(body(failure).path("error_code").asInt()).isEqualTo(3002);
      assertThat(failure.body()).doesNotContain("private fixture detail", cookie(bootstrap));
      assertThat(failure.headers().firstValue("set-cookie")).isEmpty();
    } finally {
      reset(sessions);
    }
  }

  @Test
  void bootstrapWriteFailureBecomesGeneric503WithoutIssuingCookie() throws Exception {
    doThrow(new DataAccessResourceFailureException("private write fixture detail"))
        .when(sessions)
        .createSession();
    try {
      var failure = request("GET", "/api/v1/auth/csrf", Map.of());
      assertThat(failure.statusCode()).isEqualTo(503);
      assertThat(body(failure).path("error_code").asInt()).isEqualTo(3002);
      assertThat(failure.body()).doesNotContain("private write fixture detail");
      assertThat(failure.headers().firstValue("set-cookie")).isEmpty();
    } finally {
      reset(sessions);
    }
  }

  @Test
  void outerHandlerResetsUncommittedBodyAndCookieAndPreservesCommittedFailureLimit()
      throws Exception {
    var filter = new AvailabilityFilter(new ApiErrorWriter(json));
    var request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-ID", "fixture_123");
    var response = new MockHttpServletResponse();
    filter.doFilter(
        request,
        response,
        (req, res) -> {
          var http = (jakarta.servlet.http.HttpServletResponse) res;
          http.addHeader("Set-Cookie", "SESSION=withheld-fixture");
          http.getWriter().write("pending-success");
          throw new DataAccessResourceFailureException("private fixture detail");
        });
    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getHeader("Set-Cookie")).isNull();
    assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("fixture_123");
    assertThat(response.getContentAsString())
        .doesNotContain("pending-success", "private fixture detail");
    assertThat(json.readTree(response.getContentAsString()).path("error_code").asInt())
        .isEqualTo(3002);
    var committed = new MockHttpServletResponse();
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    new MockHttpServletRequest(),
                    committed,
                    (req, res) -> {
                      res.flushBuffer();
                      throw new DataAccessResourceFailureException("private fixture detail");
                    }))
        .isInstanceOf(jakarta.servlet.ServletException.class)
        .hasMessage("Store operation failed after response commitment.")
        .hasNoCause();
    assertThat(committed.isCommitted()).isTrue();
    assertThat(committed.getStatus()).isEqualTo(200);
  }
}
