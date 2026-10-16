package com.assessment.disputeportal.common.exception;

import com.assessment.disputeportal.common.api.ErrorCode;

public final class BusinessConflictException extends RuntimeException implements CodedException {
  public BusinessConflictException(String message) {
    super(message);
  }

  @Override
  public ErrorCode errorCode() {
    return ErrorCode.ACCOUNT_STATE_CONFLICT;
  }
}
