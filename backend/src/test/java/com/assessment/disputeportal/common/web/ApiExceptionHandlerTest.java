package com.assessment.disputeportal.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.assessment.disputeportal.common.exception.BusinessConflictException;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.common.exception.InvalidSessionAccessTimeException;
import com.assessment.disputeportal.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @Test
  void mapsSharedRequestAndResourceExceptionsToStableEnvelopeCodes() {
    var invalid = handler.malformed();
    assertThat(invalid.getStatusCode().value()).isEqualTo(400);
    assertThat(invalid.getBody().error_code()).isEqualTo(1004);

    var missing = handler.notFound(new ResourceNotFoundException());
    assertThat(missing.getStatusCode().value()).isEqualTo(404);
    assertThat(missing.getBody().error_code()).isEqualTo(1003);
    assertThat(missing.getBody().error().detail()).doesNotContain("account-uuid");
  }

  @Test
  void accountStateConflictUsesItsOwnStableCode() {
    var response = handler.conflict(new BusinessConflictException("private conflict detail"));

    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody().error_code()).isEqualTo(2008);
  }

  @Test
  void sharedExceptionMessagesAreNotNeededToBuildSafeResponses() {
    var invalid = new InvalidRequestException("secret rejected input");
    var missing = new ResourceNotFoundException("Account", "id", "account-uuid");
    var conflict = new BusinessConflictException("private conflict detail");

    assertThat(handler.invalidRequest(invalid).getBody().error().detail())
        .doesNotContain(invalid.getMessage());
    assertThat(handler.notFound(missing).getBody().error().detail())
        .doesNotContain(missing.getMessage(), "account-uuid");
    assertThat(handler.conflict(conflict).getBody().error().detail())
        .doesNotContain(conflict.getMessage());
  }

  @Test
  void invalidFrameworkSessionTimeUsesTheHandledGenericServerError() {
    var failure = new InvalidSessionAccessTimeException();
    var response = handler.invalidSessionAccessTime(failure);

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody().error_code()).isEqualTo(3001);
    assertThat(response.getBody().error().detail()).doesNotContain(failure.getMessage());
  }
}
