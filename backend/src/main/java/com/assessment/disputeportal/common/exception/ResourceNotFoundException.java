package com.assessment.disputeportal.common.exception;

import com.assessment.disputeportal.common.api.ErrorCode;

public final class ResourceNotFoundException extends RuntimeException implements CodedException {
  public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
    super("%s not found with %s: '%s'.".formatted(resourceName, fieldName, fieldValue));
  }

  public ResourceNotFoundException() {
    super("The requested resource was not found.");
  }

  @Override
  public ErrorCode errorCode() {
    return ErrorCode.RESOURCE_NOT_FOUND;
  }
}
