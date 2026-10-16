package com.assessment.disputeportal.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.assessment.disputeportal.auth.model.AccountRole;
import com.assessment.disputeportal.common.web.ApiErrorWriter;
import com.assessment.disputeportal.security.session.AccountSessionValidator;
import com.assessment.disputeportal.security.session.SessionActivityStore;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import com.assessment.disputeportal.security.session.ValidatedSession;
import com.assessment.disputeportal.security.web.AuthenticatedActivity;
import com.assessment.disputeportal.security.web.AuthenticationActivityInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.*;
import org.springframework.web.method.HandlerMethod;

class AuthenticationActivityTest {
  final SessionActivityStore store = mock(SessionActivityStore.class);
  final AccountSessionValidator validator = mock(AccountSessionValidator.class);
  final AuthenticationActivityInterceptor interceptor =
      new AuthenticationActivityInterceptor(
          store, validator, new ApiErrorWriter(new ObjectMapper()));
  final ValidatedSession state =
      new ValidatedSession("fixture-session", UUID.randomUUID(), 0, AccountRole.CUSTOMER, 1000);

  static class Handlers {
    @AuthenticatedActivity
    public void interaction() {}

    public void poll() {}
  }

  HandlerMethod handler(String name) throws Exception {
    return new HandlerMethod(new Handlers(), Handlers.class.getMethod(name));
  }

  MockHttpServletRequest request(String path) {
    var request = new MockHttpServletRequest("GET", path);
    request.setServletPath(path);
    return request;
  }

  @Test
  void markedAdmittedHandlerRenewsCapturedTimeBeforeInvocationWithoutCompletionHooks()
      throws Exception {
    var request = request("/api/v1/test/eligible");
    request.setAttribute(ValidatedSession.ATTRIBUTE, state);
    when(store.renew(state.sessionId(), state.accountId(), 0, AccountRole.CUSTOMER, 1000))
        .thenReturn(true);
    assertThat(
            interceptor.preHandle(request, new MockHttpServletResponse(), handler("interaction")))
        .isTrue();
    verify(store).renew(state.sessionId(), state.accountId(), 0, AccountRole.CUSTOMER, 1000);
    // Completion/status cannot undo or repeat the committed admission.
    interceptor.afterCompletion(
        request,
        new MockHttpServletResponse(),
        handler("interaction"),
        new IllegalArgumentException("fixture validation failure"));
    verifyNoMoreInteractions(store);
  }

  @Test
  void anonymousUnmatchedPollingAndExplicitlyExcludedHandlersCannotRenew() throws Exception {
    for (String path :
        new String[] {
          "/actuator/health",
          "/actuator/health/liveness",
          "/api/v1/auth/csrf",
          "/api/v1/auth/login",
          "/api/v1/auth/logout"
        }) {
      var request = request(path);
      request.setAttribute(ValidatedSession.ATTRIBUTE, state);
      assertThat(
              interceptor.preHandle(request, new MockHttpServletResponse(), handler("interaction")))
          .isTrue();
    }
    var request = request("/api/v1/test/poll");
    request.setAttribute(ValidatedSession.ATTRIBUTE, state);
    assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handler("poll")))
        .isTrue();
    assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
        .isTrue();
    var response = new MockHttpServletResponse();
    assertThat(
            interceptor.preHandle(
                request("/api/v1/test/eligible"), response, handler("interaction")))
        .isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
    verifyNoInteractions(store);
  }

  @Test
  void zeroRowGuardRechecksAndDeniesBeforeHandlerWhileStoreFailureReturns503() throws Exception {
    var request = request("/api/v1/test/eligible");
    request.setAttribute(ValidatedSession.ATTRIBUTE, state);
    var response = new MockHttpServletResponse();
    assertThat(interceptor.preHandle(request, response, handler("interaction"))).isFalse();
    verify(validator).validate(new SessionPrincipal(state.accountId()), state.sessionId(), 1000);
    assertThat(response.getStatus()).isEqualTo(401);
    request.setAttribute(ValidatedSession.ATTRIBUTE, state);
    when(store.renew(anyString(), any(), anyLong(), any(), anyLong()))
        .thenThrow(new DataAccessResourceFailureException("private fixture"));
    response = new MockHttpServletResponse();
    assertThat(interceptor.preHandle(request, response, handler("interaction"))).isFalse();
    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getContentAsString()).contains("3002").doesNotContain("private fixture");
  }
}
