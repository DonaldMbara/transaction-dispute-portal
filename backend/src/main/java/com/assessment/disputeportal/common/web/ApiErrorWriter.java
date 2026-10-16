package com.assessment.disputeportal.common.web;

import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class ApiErrorWriter {

  private final ObjectMapper json;

  public void write(HttpServletResponse response, int status, ApiEnvelope envelope)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "no-store");
    json.writeValue(response.getOutputStream(), envelope);
  }
}
