package com.assessment.disputeportal.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpenApiContractTest extends SessionIntegrationSupport {
  @Test
  void generatedSpecListsContractRoutesAndExportsForCi() throws Exception {
    var response = request("GET", "/v3/api-docs", Map.of("Accept", "application/json"));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);

    JsonNode spec = body(response);
    JsonNode paths = spec.path("paths");
    assertThat(spec.at("/servers/0/url").asText()).isEqualTo("/");
    assertThat(paths.path("/api/v1/auth/csrf").path("get").path("parameters").isMissingNode())
        .isTrue();
    assertThat(parameterNames(paths.path("/api/v1/accounts").path("get"))).contains("page", "size");
    assertThat(parameterNames(paths.path("/api/v1/transactions").path("get")))
        .contains(
            "page",
            "size",
            "search",
            "accountId",
            "direction",
            "status",
            "fromDate",
            "toDate",
            "minAmount",
            "maxAmount",
            "sortBy",
            "sortDirection");
    assertThat(parameterNames(paths.path("/api/v1/disputes").path("get")))
        .contains(
            "page",
            "size",
            "accountId",
            "status",
            "reason",
            "fromDate",
            "toDate",
            "search",
            "sortBy",
            "sortDirection");
    assertThat(parameterNames(paths.path("/api/v1/agent/disputes").path("get")))
        .contains(
            "queue",
            "page",
            "size",
            "status",
            "reason",
            "fromDate",
            "toDate",
            "search",
            "sortBy",
            "sortDirection");
    Map<String, Set<String>> contractOperations =
        Map.ofEntries(
            Map.entry("/api/v1/auth/csrf", Set.of("get")),
            Map.entry("/api/v1/auth/login", Set.of("post")),
            Map.entry("/api/v1/auth/me", Set.of("get")),
            Map.entry("/api/v1/auth/logout", Set.of("post")),
            Map.entry("/api/v1/accounts", Set.of("get")),
            Map.entry("/api/v1/transactions", Set.of("get")),
            Map.entry("/api/v1/transactions/{id}", Set.of("get")),
            Map.entry("/api/v1/disputes", Set.of("get", "post")),
            Map.entry("/api/v1/disputes/{id}", Set.of("get")),
            Map.entry("/api/v1/agent/disputes", Set.of("get")),
            Map.entry("/api/v1/agent/disputes/{id}", Set.of("get")),
            Map.entry("/api/v1/agent/disputes/{id}/claim", Set.of("post")),
            Map.entry("/api/v1/agent/disputes/{id}/release", Set.of("post")),
            Map.entry("/api/v1/agent/disputes/{id}/resolve", Set.of("post")));
    contractOperations.forEach(
        (path, methods) ->
            methods.forEach(
                method ->
                    assertThat(paths.path(path).has(method)).as(method + " " + path).isTrue()));
    assertThat(paths.path("/api/v1/disputes").path("post").path("responses").has("201")).isTrue();
    assertThat(hasHeader(paths.path("/api/v1/disputes").path("post"), "Idempotency-Key")).isTrue();
    assertThat(hasHeader(paths.path("/api/v1/agent/disputes/{id}/claim").path("post"), "If-Match"))
        .isTrue();
    assertThat(
            hasHeader(paths.path("/api/v1/agent/disputes/{id}/release").path("post"), "If-Match"))
        .isTrue();
    assertThat(
            hasHeader(paths.path("/api/v1/agent/disputes/{id}/resolve").path("post"), "If-Match"))
        .isTrue();
    JsonNode disputeErrors = paths.path("/api/v1/disputes").path("post").path("responses");
    assertThat(disputeErrors.path("400").path("description").asText())
        .contains("1004", "/problems/validation-failed");
    assertThat(disputeErrors.path("409").path("description").asText())
        .contains("2003", "2004", "2005", "2008");
    assertThat(disputeErrors.path("412").path("description").asText()).contains("2006");
    assertThat(disputeErrors.path("428").path("description").asText()).contains("2007");
    assertThat(disputeErrors.path("503").path("description").asText()).contains("3002");
    assertThat(
            disputeErrors
                .path("409")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("$ref")
                .asText())
        .isEqualTo("#/components/schemas/ApiEnvelopeVoid");
    assertThat(spec.path("info").path("title").asText())
        .isEqualTo("Transaction Dispute Portal API");

    Path artifact = Path.of("target/openapi/openapi.json");
    Files.createDirectories(artifact.getParent());
    Files.writeString(artifact, json.writerWithDefaultPrettyPrinter().writeValueAsString(spec));
  }

  private static boolean hasHeader(JsonNode operation, String name) {
    for (JsonNode parameter : operation.path("parameters")) {
      if (name.equals(parameter.path("name").asText())
          && "header".equals(parameter.path("in").asText())) return true;
    }
    return false;
  }

  private static Set<String> parameterNames(JsonNode operation) {
    var names = new HashSet<String>();
    for (JsonNode parameter : operation.path("parameters")) {
      names.add(parameter.path("name").asText());
    }
    return names;
  }
}
