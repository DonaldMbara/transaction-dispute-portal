package com.assessment.disputeportal.security;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthenticationAuthorizationIntegrationTest extends AuthenticationApiIntegrationTest {
  @Test
  void productionRoleScopesBusinessMatchersAndDeniesProvisioningAndUnmatchedPaths()
      throws Exception {
    for (String role : new String[] {"CUSTOMER", "AGENT"}) {
      reset();
      account(role, true);
      var b = request("GET", "/api/v1/auth/csrf", Map.of());
      var r = login(cookie(b), body(b).at("/data/csrfToken").asText());
      var c = cookie(r);
      assertThat(request("GET", "/api/v1/auth/me", Map.of("Cookie", c)).statusCode())
          .isEqualTo(200);
      var transactions = request("GET", "/api/v1/transactions", Map.of("Cookie", c));
      assertThat(transactions.statusCode()).isEqualTo(role.equals("CUSTOMER") ? 200 : 403);
      if (role.equals("AGENT"))
        assertThat(body(transactions).path("error_code").asInt()).isEqualTo(1002);
      var agentQueue = request("GET", "/api/v1/agent/disputes", Map.of("Cookie", c));
      assertThat(agentQueue.statusCode()).isEqualTo(role.equals("AGENT") ? 200 : 403);
      if (role.equals("CUSTOMER"))
        assertThat(body(agentQueue).path("error_code").asInt()).isEqualTo(1002);
      for (String path : new String[] {"/api/v1/auth/provision", "/provision", "/error"}) {
        var response = request("GET", path, Map.of("Cookie", c));
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(body(response).path("error_code").asInt()).isEqualTo(1002);
        assertThat(response.headers().firstValue("Location")).isEmpty();
      }
    }
  }
}
