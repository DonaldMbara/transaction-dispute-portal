package com.assessment.disputeportal.dispute.repository;

import com.assessment.disputeportal.dispute.model.Dispute;
import com.assessment.disputeportal.dispute.model.DisputeEvent;
import java.util.Optional;
import java.util.UUID;

public interface AgentDisputeWriteRepository {
  Optional<Dispute> lockById(UUID id);

  boolean update(Dispute before, Dispute after);

  void append(DisputeEvent event);
}
