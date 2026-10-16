package com.assessment.disputeportal.common.web;

import org.junit.jupiter.api.Test;

import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.common.exception.BusinessConflictException;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.common.exception.ResourceNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsSharedRequestAndResourceExceptionsToStableEnvelopeCodes() {
        var invalid = handler.malformed();
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(invalid.getBody().error_code()).isEqualTo(1004);

        var missing = handler.notFound();
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(missing.getBody().error_code()).isEqualTo(1003);
        assertThat(missing.getBody().error().detail()).doesNotContain("account-uuid");
    }

    @Test
    void accountStateConflictUsesItsOwnStableCode() {
        var response = handler.conflict();

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().error_code()).isEqualTo(2008);
    }

    @Test
    void sharedExceptionMessagesAreNotNeededToBuildSafeResponses() {
        var invalid = new InvalidRequestException("secret rejected input");
        var missing = new ResourceNotFoundException("Account", "id", "account-uuid");
        var conflict = new BusinessConflictException("private conflict detail");

        assertThat(handler.malformed().getBody().error().detail()).doesNotContain(invalid.getMessage());
        assertThat(handler.notFound().getBody().error().detail()).doesNotContain(missing.getMessage(), "account-uuid");
        assertThat(handler.conflict().getBody().error().detail()).doesNotContain(conflict.getMessage());
    }
}