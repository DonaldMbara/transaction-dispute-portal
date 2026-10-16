package com.assessment.disputeportal.security;

import com.assessment.disputeportal.DisputePortalApplication;
import com.assessment.disputeportal.auth.cli.AccountAdministrationService;
import com.assessment.disputeportal.auth.model.AccountIdentity;
import com.assessment.disputeportal.auth.service.AccountAdministrationTestConfiguration;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class MultiInstanceSupport {
  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse(
                  "postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3")
              .asCompatibleSubstituteFor("postgres"));

  ServletWebServerApplicationContext a, b;
  JdbcTemplate jdbc;
  final ObjectMapper json = new ObjectMapper();
  final HttpClient client =
      HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
  static final String PASSWORD = "isolated multi instance password";

  Class<?>[] extraSources() {
    return new Class<?>[] {AccountAdministrationTestConfiguration.class};
  }

  ServletWebServerApplicationContext start() {
    return (ServletWebServerApplicationContext)
        new SpringApplicationBuilder(DisputePortalApplication.class)
            .sources(extraSources())
            .profiles("test")
            .properties(
                Map.of(
                    "DB_URL",
                    postgres.getJdbcUrl(),
                    "DB_USERNAME",
                    postgres.getUsername(),
                    "DB_PASSWORD",
                    postgres.getPassword()))
            .run("--server.port=0");
  }

  @BeforeAll
  void instances() {
    a = start();
    b = start();
    jdbc = a.getBean(JdbcTemplate.class);
  }

  @BeforeEach
  void reset() {
    jdbc.update("DELETE FROM spring_session");
    jdbc.update("DELETE FROM app_users");
  }

  @AfterAll
  void close() {
    if (a != null) a.close();
    if (b != null) b.close();
  }

  HttpResponse<String> send(
      ServletWebServerApplicationContext context,
      String method,
      String path,
      String cookie,
      String token,
      String payload)
      throws Exception {
    var req =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + context.getWebServer().getPort() + path))
            .timeout(java.time.Duration.ofSeconds(15))
            .method(
                method,
                payload == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(payload));
    if (cookie != null) req.header("Cookie", cookie);
    if (token != null) req.header("X-CSRF-TOKEN", token);
    if (payload != null) req.header("Content-Type", "application/json");
    return client.send(req.build(), HttpResponse.BodyHandlers.ofString());
  }

  String cookie(HttpResponse<String> response) {
    return response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
  }

  String token(ServletWebServerApplicationContext context, String cookie) throws Exception {
    return json.readTree(send(context, "GET", "/api/v1/auth/csrf", cookie, null, null).body())
        .at("/data/csrfToken")
        .asText();
  }

  String login(ServletWebServerApplicationContext context, String existing) throws Exception {
    var bootstrap = send(context, "GET", "/api/v1/auth/csrf", existing, null, null);
    String c = existing == null ? cookie(bootstrap) : existing;
    var result =
        send(
            context,
            "POST",
            "/api/v1/auth/login",
            c,
            json.readTree(bootstrap.body()).at("/data/csrfToken").asText(),
            json.writeValueAsString(Map.of("email", "multi@example.test", "password", PASSWORD)));
    org.assertj.core.api.Assertions.assertThat(result.statusCode()).isEqualTo(200);
    return cookie(result);
  }

  AccountIdentity create() {
    return a.getBean(AccountAdministrationService.class)
        .create("multi@example.test", "CUSTOMER", true, PASSWORD, "isolated-test");
  }

  int identity(ServletWebServerApplicationContext context, String cookie) throws Exception {
    return send(context, "GET", "/api/v1/auth/me", cookie, null, null).statusCode();
  }

  String sessionId(String cookie) {
    return new String(
        Base64.getDecoder().decode(cookie.substring(8)), java.nio.charset.StandardCharsets.UTF_8);
  }
}
