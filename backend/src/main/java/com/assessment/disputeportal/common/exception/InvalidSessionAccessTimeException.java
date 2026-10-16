package com.assessment.disputeportal.common.exception;

import com.assessment.disputeportal.common.api.ErrorCode;

public final class InvalidSessionAccessTimeException extends RuntimeException
    implements CodedException {
  public InvalidSessionAccessTimeException() {
    super("Invalid framework session access time.");
  }

  @Override
  public ErrorCode errorCode() {
    return ErrorCode.INTERNAL_ERROR;
  }
}
