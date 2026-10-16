package com.assessment.disputeportal.security.web;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.common.web.ApiErrorWriter;
import com.assessment.disputeportal.common.web.StoreFailureClassifier;
import com.assessment.disputeportal.security.config.SecurityConfiguration;
import com.assessment.disputeportal.security.session.AccountSessionValidationFilter;
import com.assessment.disputeportal.security.session.AccountSessionValidator;
import com.assessment.disputeportal.security.session.SessionActivityStore;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import com.assessment.disputeportal.security.session.ValidatedSession;

@Component
@RequiredArgsConstructor
public final class AuthenticationActivityInterceptor implements HandlerInterceptor {
    private final SessionActivityStore activity;
    private final AccountSessionValidator validator;
    private final ApiErrorWriter errors;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(AuthenticatedActivity.class)
                || SecurityConfiguration.isHealthRequest(request)
                || Set.of("/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/auth/logout")
                        .contains(request.getServletPath())) {
            return true;
        }
        Object attribute = request.getAttribute(ValidatedSession.ATTRIBUTE);
        if (!(attribute instanceof ValidatedSession state)) {
            AccountSessionValidationFilter.unauthorized(response, errors);
            return false;
        }
        try {
            if (activity.renew(
                    state.sessionId(), state.accountId(), state.version(), state.role(), state.accessTime())) {
                return true;
            }
            // Fresh read-only inspection after a lost guard, never re-load/save a second mutable Session copy.
            validator.validate(new SessionPrincipal(state.accountId()), state.sessionId(), state.accessTime());
            AccountSessionValidationFilter.clear(request);
            AccountSessionValidationFilter.unauthorized(response, errors);
            return false;
        } catch (RuntimeException failure) {
            if (!StoreFailureClassifier.isStoreFailure(failure)) {
                throw failure;
            }
            errors.write(response, 503, ApiEnvelope.unavailable());
            return false;
        }
    }
}
