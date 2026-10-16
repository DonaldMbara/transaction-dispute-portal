package com.assessment.disputeportal.auth.controller;

import com.assessment.disputeportal.auth.model.AccountIdentity;
import com.assessment.disputeportal.auth.model.dto.IdentityResponse;
import com.assessment.disputeportal.auth.model.dto.LoginRequest;
import com.assessment.disputeportal.auth.service.LoginService;
import com.assessment.disputeportal.auth.service.LogoutService;
import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.security.session.SessionPrincipal;
import com.assessment.disputeportal.security.web.AuthenticatedActivity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public final class AuthController {

  private final LoginService loginService;
  private final LogoutService logoutService;

  @PostMapping("/login")
  ApiEnvelope<IdentityResponse> login(
      @Valid @RequestBody LoginRequest input,
      HttpServletRequest request,
      HttpServletResponse response) {
    AccountIdentity identity =
        loginService.login(
            input.email(),
            input.password(),
            response.getHeader("X-Correlation-ID"),
            request,
            response);
    return ApiEnvelope.success(toResponse(identity));
  }

  @AuthenticatedActivity
  @GetMapping("/me")
  ApiEnvelope<IdentityResponse> identity() {
    var principal =
        (SessionPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return ApiEnvelope.success(toResponse(loginService.currentIdentity(principal)));
  }

  @PostMapping("/logout")
  ApiEnvelope<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    logoutService.logout(request, response, response.getHeader("X-Correlation-ID"));
    return ApiEnvelope.success(null);
  }

  private static IdentityResponse toResponse(AccountIdentity identity) {
    return new IdentityResponse(identity.id(), identity.email(), identity.role());
  }
}
