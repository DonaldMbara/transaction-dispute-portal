package com.assessment.disputeportal.dispute.repository;

import com.assessment.disputeportal.dispute.model.dto.AgentDisputeDetailResponse;
import com.assessment.disputeportal.dispute.model.dto.AgentDisputeListItemResponse;
import com.assessment.disputeportal.dispute.model.query.AgentDisputeQuery;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface AgentDisputeReadRepository {
  Page<AgentDisputeListItemResponse> find(AgentDisputeQuery query);

  Optional<AgentDisputeDetailResponse> findById(UUID disputeId);
}
