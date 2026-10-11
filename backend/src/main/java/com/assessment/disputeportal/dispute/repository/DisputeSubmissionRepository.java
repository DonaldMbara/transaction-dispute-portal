package com.assessment.disputeportal.dispute.repository;

import java.time.Instant;
import java.util.UUID;

import com.assessment.disputeportal.dispute.model.Dispute;
import com.assessment.disputeportal.dispute.model.DisputeCreatedEvent;

public interface DisputeSubmissionRepository {
    boolean existsForTransaction(UUID transactionId);
    void insert(Dispute dispute);
    void appendCreated(DisputeCreatedEvent event);
}
