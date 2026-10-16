package com.assessment.disputeportal.common.exception;

public final class InvalidSessionAccessTimeException extends RuntimeException {
  public InvalidSessionAccessTimeException() {
    super("Invalid framework session access time.");
  }
}
