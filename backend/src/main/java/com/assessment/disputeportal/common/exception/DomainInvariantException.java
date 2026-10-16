package com.assessment.disputeportal.common.exception;

import com.assessment.disputeportal.common.api.ErrorCode;

public final class DomainInvariantException extends RuntimeException implements CodedException {
  public DomainInvariantException(String message) {
    super(message);
  }

  public DomainInvariantException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public ErrorCode errorCode() {
    return ErrorCode.INTERNAL_ERROR;
  }
}
