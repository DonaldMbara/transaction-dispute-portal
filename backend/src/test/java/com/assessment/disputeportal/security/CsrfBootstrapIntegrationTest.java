package com.assessment.disputeportal.security;

import org.junit.jupiter.api.Test;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CsrfBootstrapIntegrationTest extends SessionIntegrationSupport {
    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.boot.autoconfigure.session.JdbcSessionProperties properties;

    @Test void anonymousMaskedTokenPersistsBeforeSuccessAndWorksAsHeader() throws Exception {
        var bootstrap = request("GET", "/api/v1/auth/csrf", Map.of());
        assertThat(bootstrap.statusCode()).isEqualTo(200);
        var data = body(bootstrap).path("data");
        assertThat(data.fieldNames()).toIterable().containsExactlyInAnyOrder("csrfToken", "headerName");
        assertThat(data.path("headerName").asText()).isEqualTo("X-CSRF-TOKEN");
        String cookie = cookie(bootstrap);
        org.springframework.session.Session session = sessions.findById(sessionId(cookie));
        assertThat(session).isNotNull();
        assertThat(session.getMaxInactiveInterval()).isEqualTo(java.time.Duration.ofMinutes(30));
        assertThat(session.getAttributeNames()).containsExactly("org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN");
        CsrfToken stored = session.getAttribute("org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN");
        assertThat(data.path("csrfToken").asText()).isNotEqualTo(stored.getToken());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_session_activity", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users", Integer.class)).isZero();
        var allowedCsrf = request("POST", "/api/v1/auth/login", Map.of("Cookie",cookie,"X-CSRF-TOKEN",data.path("csrfToken").asText()));
        assertThat(allowedCsrf.statusCode()).isEqualTo(400); // CSRF passed; implemented login rejects its missing JSON body.
        assertThat(body(allowedCsrf).path("error_code").asInt()).isEqualTo(1004);
    }
    @Test void missingInvalidForeignAndStaleTokensAreRejectedBeforeAuthorization() throws Exception {
        var a=request("GET","/api/v1/auth/csrf",Map.of());
        var b=request("GET","/api/v1/auth/csrf",Map.of());
        String cookie=cookie(a), token=body(a).path("data").path("csrfToken").asText();
        for (Map<String,String> headers : java.util.List.of(Map.of("Cookie",cookie),
                Map.of("Cookie",cookie,"X-CSRF-TOKEN","invalid"),
                Map.of("Cookie",cookie,"X-CSRF-TOKEN",body(b).path("data").path("csrfToken").asText()))) {
            var rejected=request("POST","/api/v1/auth/logout",headers);
            assertThat(rejected.statusCode()).isEqualTo(403);
            assertThat(body(rejected).path("error_code").asInt()).isEqualTo(1010);
            assertThat(rejected.body()).doesNotContain(token,cookie);
        }
        sessions.deleteById(sessionId(cookie));
        var stale=request("POST","/api/v1/auth/logout",Map.of("Cookie",cookie,"X-CSRF-TOKEN",token));
        assertThat(stale.statusCode()).isEqualTo(403);
        assertThat(body(stale).path("error_code").asInt()).isEqualTo(1010);
    }
    @Test void healthNeverLoadsOrTouchesSuppliedSessionAndNonGetHealthIsDenied() throws Exception {
        var bootstrap=request("GET","/api/v1/auth/csrf",Map.of());
        String cookie=cookie(bootstrap);
        long access=jdbc.queryForObject("SELECT last_access_time FROM spring_session WHERE session_id=?",Long.class,sessionId(cookie));
        clearInvocations(sessions);
        for(String path : new String[]{"/actuator/health","/actuator/health/readiness","/actuator/health/liveness"}) {
            var result=request("GET",path,Map.of("Cookie",cookie));
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(body(result).has("details")).isFalse();
            assertThat(result.headers().firstValue("set-cookie")).isEmpty();
        }
        assertThat(mockingDetails(sessions).getInvocations().stream().filter(call -> call.getMethod().getName().equals("findById")).count()).isZero();
        assertThat(jdbc.queryForObject("SELECT last_access_time FROM spring_session WHERE session_id=?",Long.class,sessionId(cookie))).isEqualTo(access);
        assertThat(request("POST","/actuator/health",Map.of()).statusCode()).isEqualTo(403);
        assertThat(request("GET","/actuator/health/db",Map.of()).statusCode()).isEqualTo(401);
    }
    @Test void urlSessionIdentifiersAndQueryTokensCannotAuthorizeRequests() throws Exception {
        var bootstrap=request("GET","/api/v1/auth/csrf",Map.of());
        String cookie=cookie(bootstrap), token=body(bootstrap).path("data").path("csrfToken").asText();
        assertThat(request("POST","/api/v1/auth/login?SESSION="+sessionId(cookie),Map.of("X-CSRF-TOKEN",token)).statusCode()).isEqualTo(403);
        assertThat(request("POST","/api/v1/auth/login?csrfToken="+token,Map.of("Cookie",cookie)).statusCode()).isEqualTo(403);
        assertThat(request("GET","/api/v1/auth/csrf;jsessionid="+sessionId(cookie),Map.of()).statusCode()).isEqualTo(400);
    }
    @Test void immediateFlushIsObservableBeforeExplicitRepositorySave() {
        assertThat(properties.getFlushMode()).isEqualTo(org.springframework.session.FlushMode.IMMEDIATE);
        assertThat(properties.getSaveMode()).isEqualTo(org.springframework.session.SaveMode.ON_SET_ATTRIBUTE);
        assertThat(properties.getInitializeSchema()).isEqualTo(org.springframework.boot.sql.init.DatabaseInitializationMode.NEVER);
        org.springframework.session.Session session=sessions.createSession();
        try {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE session_id=?",Integer.class,session.getId())).isEqualTo(1);
            session.setAttribute("test-only", "fixture");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session_attributes a JOIN spring_session s ON a.session_primary_id=s.primary_id WHERE s.session_id=? AND a.attribute_name='test-only'",Integer.class,session.getId())).isEqualTo(1);
        } finally { sessions.deleteById(session.getId()); }
    }
}
