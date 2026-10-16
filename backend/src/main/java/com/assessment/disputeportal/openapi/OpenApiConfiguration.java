package com.assessment.disputeportal.openapi;

import com.assessment.disputeportal.common.api.ErrorCode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"local", "test"})
public class OpenApiConfiguration {
  @Bean
  OpenApiCustomizer standardErrorResponses() {
    return openApi -> {
      Map<Integer, String> descriptions =
          Arrays.stream(ErrorCode.values())
              .collect(
                  Collectors.groupingBy(
                      code -> code.status().value(),
                      Collectors.mapping(
                          code ->
                              "%s %s (/problems/%s)"
                                  .formatted(code.code(), code.name(), code.type()),
                          Collectors.joining(", "))));
      openApi
          .getPaths()
          .values()
          .forEach(
              path ->
                  path.readOperations()
                      .forEach(
                          operation ->
                              descriptions.forEach(
                                  (status, description) ->
                                      operation
                                          .getResponses()
                                          .addApiResponse(
                                              status.toString(),
                                              new ApiResponse()
                                                  .description(
                                                      "Standard JSON error envelope: "
                                                          + description)
                                                  .content(
                                                      new Content()
                                                          .addMediaType(
                                                              "application/json",
                                                              new MediaType()
                                                                  .schema(
                                                                      new Schema<>()
                                                                          .$ref(
                                                                              "#/components/schemas/ApiEnvelopeVoid"))))))));
    };
  }

  @Bean
  OpenAPI disputePortalOpenApi() {
    return new OpenAPI()
        .servers(List.of(new Server().url("/").description("Same-origin application")))
        .info(
            new Info()
                .title("Transaction Dispute Portal API")
                .version("v1")
                .description(
                    "Same-origin JSON API for customer accounts, transactions, and disputes. "
                        + "The API contract and stable error catalogue are documented in "
                        + "docs/design/api-contract.md."));
  }
}
