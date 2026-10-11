package com.assessment.disputeportal.auth.model;

import com.assessment.disputeportal.common.exception.InvalidRequestException;

public enum AccountRole {
    CUSTOMER, AGENT;

    public static AccountRole parse(String value) {
        if ("CUSTOMER".equals(value)) return CUSTOMER;
        if ("AGENT".equals(value)) return AGENT;
        throw new InvalidRequestException("Unsupported account role.");
    }
}
