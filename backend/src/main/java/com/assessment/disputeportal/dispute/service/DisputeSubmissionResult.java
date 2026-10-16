package com.assessment.disputeportal.dispute.service;

import com.assessment.disputeportal.dispute.model.dto.DisputeSubmissionResponse;

public record DisputeSubmissionResult(DisputeSubmissionResponse data) {
  public String disputeId() {
    return data.id().toString();
  }
}
