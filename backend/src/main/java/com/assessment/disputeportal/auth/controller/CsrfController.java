package com.assessment.disputeportal.auth.controller;

import com.assessment.disputeportal.auth.model.dto.CsrfBootstrapResponse;
import com.assessment.disputeportal.common.api.ApiEnvelope;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public final class CsrfController {
  @GetMapping("/csrf")
  ApiEnvelope<?> bootstrap(CsrfToken token) {
    return ApiEnvelope.success(new CsrfBootstrapResponse(token.getToken(), token.getHeaderName()));
  }
}
