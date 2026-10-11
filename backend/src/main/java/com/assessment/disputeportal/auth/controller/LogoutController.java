package com.assessment.disputeportal.auth.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.assessment.disputeportal.auth.service.LogoutService;
import com.assessment.disputeportal.common.ApiEnvelope;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequiredArgsConstructor
public final class LogoutController {

    private final LogoutService logoutService;

    @PostMapping("/api/v1/auth/logout")
    ApiEnvelope logout(HttpServletRequest request, HttpServletResponse response) {
        logoutService.logout(request, response, response.getHeader("X-Correlation-ID"));
        return ApiEnvelope.success(null);
    }
}
