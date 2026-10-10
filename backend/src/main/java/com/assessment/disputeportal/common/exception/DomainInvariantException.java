package com.assessment.disputeportal.common.exception;

public final class DomainInvariantException extends RuntimeException {
    public DomainInvariantException(String message) {
        super(message);
    }

    public DomainInvariantException(String message, Throwable cause) {
        super(message, cause);
    }
}