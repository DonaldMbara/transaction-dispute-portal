package com.assessment.disputeportal.common.exception;

import com.assessment.disputeportal.common.api.ErrorCode;

public final class InvalidOperationException extends RuntimeException implements CodedException {
  public InvalidOperationException(String message) {
    super(message);
  }

  @Override
  public ErrorCode errorCode() {
    return ErrorCode.DISPUTE_STATE_CONFLICT;
  }
}
