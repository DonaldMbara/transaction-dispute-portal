package com.assessment.disputeportal.auth.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.assessment.disputeportal.auth.model.dto.IdentityResponse;
import com.assessment.disputeportal.auth.model.dto.LoginRequest;
import com.assessment.disputeportal.auth.service.LoginService;
import com.assessment.disputeportal.auth.model.AccountIdentity;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.security.web.AuthenticatedActivity;
import com.assessment.disputeportal.common.exception.InvalidRequestException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequiredArgsConstructor
public final class AuthController {

    private final LoginService loginService;

    @PostMapping("/api/v1/auth/login")
    ApiEnvelope login(@RequestBody LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        if (input == null) throw new InvalidRequestException("Invalid login request.");
        AccountIdentity identity = loginService.login(
                input.email, input.password, response.getHeader("X-Correlation-ID"), request, response);
        input.password = null;
        return ApiEnvelope.success(toResponse(identity));
    }

    @AuthenticatedActivity
    @GetMapping("/api/v1/auth/me")
    ApiEnvelope identity() {
        var principal = (SessionPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiEnvelope.success(toResponse(loginService.currentIdentity(principal)));
    }

    private static IdentityResponse toResponse(AccountIdentity identity) {
        return new IdentityResponse(identity.id(), identity.email(), identity.role());
    }
}
