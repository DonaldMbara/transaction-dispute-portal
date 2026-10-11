package com.assessment.disputeportal.security;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class AuthenticationAuthorizationIntegrationTest extends AuthenticationApiIntegrationTest {
    @Test void productionRemainsDefaultDeniedWithNoPublicProvisioningOrBusinessMatchers() throws Exception {
        for(String role:new String[]{"CUSTOMER","AGENT"}) {
            reset();account(role,true);var b=request("GET","/api/v1/auth/csrf",Map.of());var r=login(cookie(b),body(b).at("/data/csrfToken").asText());var c=cookie(r);
            assertThat(request("GET","/api/v1/auth/me",Map.of("Cookie",c)).statusCode()).isEqualTo(200);
            for(String path:new String[]{"/api/v1/transactions","/api/v1/agent/disputes","/api/v1/auth/provision","/provision","/error"}) {
                var response=request("GET",path,Map.of("Cookie",c));assertThat(response.statusCode()).isEqualTo(403);
                assertThat(body(response).path("error_code").asInt()).isEqualTo(1002);assertThat(response.headers().firstValue("Location")).isEmpty();
            }
        }
    }
}
