package com.assessment.disputeportal.common.exception;

import com.assessment.disputeportal.common.api.ErrorCode;

public final class InvalidRequestException extends RuntimeException implements CodedException {
  public InvalidRequestException(String message) {
    super(message);
  }

  public InvalidRequestException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public ErrorCode errorCode() {
    return ErrorCode.INVALID_REQUEST;
  }
}
