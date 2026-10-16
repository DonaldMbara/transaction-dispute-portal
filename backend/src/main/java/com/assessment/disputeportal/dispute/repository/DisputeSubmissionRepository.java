package com.assessment.disputeportal.dispute.repository;

import com.assessment.disputeportal.dispute.model.Dispute;
import com.assessment.disputeportal.dispute.model.DisputeEvent;
import java.util.UUID;

public interface DisputeSubmissionRepository {
  boolean existsForTransaction(UUID transactionId);

  void insert(Dispute dispute);

  void appendCreated(DisputeEvent event);
}
