package com.assessment.disputeportal.common.exception;

public final class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super("%s not found with %s: '%s'.".formatted(resourceName, fieldName, fieldValue));
    }
}