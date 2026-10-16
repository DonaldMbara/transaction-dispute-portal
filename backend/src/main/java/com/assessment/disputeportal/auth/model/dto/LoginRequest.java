package com.assessment.disputeportal.auth.model.dto;

import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;

/** Strict login body: only textual email/password are accepted; unknown fields are rejected. */
public final class LoginRequest {
  public String email;
  public String password;

  @JsonSetter("email")
  public void email(JsonNode value) {
    if (!value.isTextual()) throw new InvalidRequestException("Invalid login field.");
    email = value.asText();
  }

  @JsonSetter("password")
  public void password(JsonNode value) {
    if (!value.isTextual()) throw new InvalidRequestException("Invalid login field.");
    password = value.asText();
  }

  @JsonAnySetter
  public void reject(String name, Object value) {
    throw new InvalidRequestException("Unknown login field.");
  }

  @Override
  public String toString() {
    return "LoginRequest[redacted]";
  }
}
